package info.kgeorgiy.ja.Gebesht.common;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class NetworkUtilities {
    private NetworkUtilities() {}

    public static String packetToString(DatagramPacket packet) {
        return new String(
                packet.getData(),
                packet.getOffset(),
                packet.getLength(),
                StandardCharsets.UTF_8
        );
    }

    public static String bufferToString(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        buffer.rewind();
        return new String(data, StandardCharsets.UTF_8);
    }

    public static DatagramPacket bytesToPacket(byte[] bytes) {
        return new DatagramPacket(bytes, bytes.length);
    }

    public static DatagramPacket bytesToPacket(byte[] bytes, final InetSocketAddress address) {
        return new DatagramPacket(bytes, bytes.length, address);
    }

    public static DatagramPacket bytesToPacket(byte[] bytes, final SocketAddress address) {
        return new DatagramPacket(bytes, bytes.length, address);
    }

    public static byte[] stringToBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    public static ByteBuffer stringToBuffer(String string) {
        return ByteBuffer.wrap(stringToBytes(string));
    }
}
