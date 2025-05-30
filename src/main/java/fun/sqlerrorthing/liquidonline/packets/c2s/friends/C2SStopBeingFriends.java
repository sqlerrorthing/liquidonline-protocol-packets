package fun.sqlerrorthing.liquidonline.packets.c2s.friends;

import fun.sqlerrorthing.liquidonline.packets.Packet;
import fun.sqlerrorthing.liquidonline.packets.PacketBound;
import fun.sqlerrorthing.liquidonline.packets.SerializedName;
import fun.sqlerrorthing.liquidonline.packets.strategy.impl.netty.compilertime.UnsignedNumber;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Request to end friendship with the specified player
 */
@Data
@SuperBuilder
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
public class C2SStopBeingFriends implements Packet {
    /**
     * Friend's id, with whom to delete friendship
     */
    @SerializedName("i")
    @UnsignedNumber
    int friendId;

    @Override
    public byte id() {
        return 11;
    }

    @Override
    public @org.jetbrains.annotations.NotNull PacketBound packetBound() {
        return PacketBound.CLIENT;
    }
}
