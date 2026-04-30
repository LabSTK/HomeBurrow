from datetime import datetime, timedelta, timezone

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.models.group import Group, GroupMembership
from app.models.message import Message
from app.models.user import User


async def _create_user(
    session,
    email: str,
    display_name: str,
    *,
    must_change_password: bool = False,
) -> User:
    user = User(
        email=email,
        display_name=display_name,
        password_hash="test-hash",
        must_change_password=must_change_password,
        is_admin=False,
        token_version=0,
    )
    session.add(user)
    await session.flush()
    return user


async def _create_group_with_members(
    session,
    owner: User,
    *members: User,
) -> Group:
    group = Group(name="Test Group", created_by=owner.id)
    session.add(group)
    await session.flush()

    session.add(GroupMembership(group_id=group.id, user_id=owner.id, role="owner"))
    for member in members:
        session.add(GroupMembership(group_id=group.id, user_id=member.id, role="member"))

    await session.commit()
    await session.refresh(group)
    return group


def _auth_headers(user: User) -> dict[str, str]:
    return {"X-Test-User": str(user.id)}


async def test_membership_required_for_send_and_list(
    client: AsyncClient,
    session_factory: async_sessionmaker,
) -> None:
    async with session_factory() as session:
        member = await _create_user(session, "member@example.com", "Member")
        outsider = await _create_user(session, "outsider@example.com", "Outsider")
        group = await _create_group_with_members(session, member)

    headers = _auth_headers(outsider)

    list_response = await client.get(f"/groups/{group.id}/messages", headers=headers)
    assert list_response.status_code == 403
    assert list_response.json()["detail"]["code"] == "NOT_GROUP_MEMBER"

    send_response = await client.post(
        f"/groups/{group.id}/messages",
        headers=headers,
        json={"body": "hello"},
    )
    assert send_response.status_code == 403
    assert send_response.json()["detail"]["code"] == "NOT_GROUP_MEMBER"


async def test_send_then_list_messages(
    client: AsyncClient,
    session_factory: async_sessionmaker,
) -> None:
    async with session_factory() as session:
        member = await _create_user(session, "sender@example.com", "Sender Name")
        group = await _create_group_with_members(session, member)

    headers = _auth_headers(member)

    send_response = await client.post(
        f"/groups/{group.id}/messages",
        headers=headers,
        json={"body": " Hello chat "},
    )
    assert send_response.status_code == 201
    sent_payload = send_response.json()
    assert sent_payload["body"] == "Hello chat"
    assert sent_payload["sender_user_id"] == str(member.id)
    assert sent_payload["sender_display_name"] == "Sender Name"
    assert sent_payload["created_at"] is not None

    list_response = await client.get(f"/groups/{group.id}/messages", headers=headers)
    assert list_response.status_code == 200

    list_payload = list_response.json()
    assert list_payload["has_more"] is False
    assert list_payload["next_before"] is None
    assert len(list_payload["items"]) == 1
    assert list_payload["items"][0]["id"] == sent_payload["id"]
    assert list_payload["items"][0]["sender_display_name"] == "Sender Name"


async def test_message_pagination_with_before_and_limit(
    client: AsyncClient,
    session_factory: async_sessionmaker,
) -> None:
    async with session_factory() as session:
        member = await _create_user(session, "pager@example.com", "Pager")
        group = await _create_group_with_members(session, member)
        base_time = datetime.now(timezone.utc)
        session.add_all(
            [
                Message(
                    group_id=group.id,
                    sender_user_id=member.id,
                    body="one",
                    created_at=base_time,
                ),
                Message(
                    group_id=group.id,
                    sender_user_id=member.id,
                    body="two",
                    created_at=base_time + timedelta(seconds=1),
                ),
                Message(
                    group_id=group.id,
                    sender_user_id=member.id,
                    body="three",
                    created_at=base_time + timedelta(seconds=2),
                ),
            ]
        )
        await session.commit()

    headers = _auth_headers(member)

    first_page = await client.get(
        f"/groups/{group.id}/messages",
        headers=headers,
        params={"limit": 2},
    )
    assert first_page.status_code == 200
    first_payload = first_page.json()
    assert len(first_payload["items"]) == 2
    assert first_payload["has_more"] is True
    assert first_payload["next_before"] == first_payload["items"][-1]["id"]

    second_page = await client.get(
        f"/groups/{group.id}/messages",
        headers=headers,
        params={"before": first_payload["next_before"], "limit": 2},
    )
    assert second_page.status_code == 200
    second_payload = second_page.json()
    assert len(second_payload["items"]) == 1
    assert second_payload["has_more"] is False
    assert second_payload["next_before"] is None

    first_ids = {item["id"] for item in first_payload["items"]}
    second_ids = {item["id"] for item in second_payload["items"]}
    assert first_ids.isdisjoint(second_ids)
    assert len(first_ids | second_ids) == 3


async def test_list_messages_invalid_limit_constraints(
    client: AsyncClient,
    session_factory: async_sessionmaker,
) -> None:
    async with session_factory() as session:
        member = await _create_user(session, "limit@example.com", "Limiter")
        group = await _create_group_with_members(session, member)

    headers = _auth_headers(member)

    too_small = await client.get(
        f"/groups/{group.id}/messages",
        headers=headers,
        params={"limit": 0},
    )
    assert too_small.status_code == 422
    assert any(
        issue["loc"] == ["query", "limit"]
        for issue in too_small.json()["detail"]
    )

    too_large = await client.get(
        f"/groups/{group.id}/messages",
        headers=headers,
        params={"limit": 101},
    )
    assert too_large.status_code == 422
    assert any(
        issue["loc"] == ["query", "limit"]
        for issue in too_large.json()["detail"]
    )
