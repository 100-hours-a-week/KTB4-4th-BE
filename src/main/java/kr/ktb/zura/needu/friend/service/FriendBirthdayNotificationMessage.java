package kr.ktb.zura.needu.friend.service;

record FriendBirthdayNotificationMessage(String title, String body) {

    static FriendBirthdayNotificationMessage from(String nickname, int reminderDay) {
        return switch (reminderDay) {
            case 7 -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이 7일 남았어요.",
                    "미리 취향에 맞는 선물을 골라 여유 있게 준비해 보세요.");
            case 5 -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이 5일 남았어요.",
                    "선물을 고민 중이라면 지금 추천 상품을 확인해 보세요.");
            case 3 -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이 3일 남았어요.",
                    "생일이 가까워졌어요. 마음에 드는 선물을 지금 골라보세요.");
            case 1 -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이 하루 남았어요.",
                    "늦기 전에 취향에 맞는 선물을 준비해 보세요.");
            case 0 -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이에요.",
                    "오늘 마음을 담은 선물로 생일을 축하해 주세요.");
            default -> new FriendBirthdayNotificationMessage(
                    nickname + " 님의 생일이 " + reminderDay + "일 남았어요.",
                    "취향에 맞는 선물을 미리 준비해 보세요.");
        };
    }
}
