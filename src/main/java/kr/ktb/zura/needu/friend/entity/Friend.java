package kr.ktb.zura.needu.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "friends", uniqueConstraints =
        @UniqueConstraint(name = "uk_friends_owner_friend", columnNames = {"owner_user_id", "friend_user_id"}))
@NoArgsConstructor(access = PROTECTED)
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "friend_user_id", nullable = false)
    private Long friendUserId;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    public Friend(Long ownerUserId, Long friendUserId) {
        this.ownerUserId = ownerUserId;
        this.friendUserId = friendUserId;
    }

    public void markAsFavorite() {
        this.favorite = true;
    }

    public void unmarkAsFavorite() {
        this.favorite = false;
    }
}
