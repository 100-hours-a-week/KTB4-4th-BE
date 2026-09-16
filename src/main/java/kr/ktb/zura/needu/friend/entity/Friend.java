package kr.ktb.zura.needu.friend.entity;

import jakarta.persistence.*;
import kr.ktb.zura.needu.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "friends")
@NoArgsConstructor(access = PROTECTED)
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_user_id", nullable = false)
    private User friendUser;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    public Friend(User ownerUser, User friendUser) {
        this.ownerUser = ownerUser;
        this.friendUser = friendUser;
    }

    public void markAsFavorite() {
        this.favorite = true;
    }

    public void unmarkAsFavorite() {
        this.favorite = false;
    }
}