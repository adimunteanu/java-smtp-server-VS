import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Server {
    public static void main(String[] args) throws IOException {
        // Selector: multiplexor of SelectableChannel objects
        Selector selector = null; // selector is open here
        try {
            selector = Selector.open();
        } catch (IOException e1) {
            e1.printStackTrace();
            System.exit(1);
        }

        // ServerSocketChannel: selectable channel for stream-oriented listening sockets
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 1111);

        // Binds the channel's serverChannel to a local address and configures the serverChannel to listen for connections
        serverChannel.bind(serverAddress);

        // Adjusts this channel's blocking mode.
        serverChannel.configureBlocking(false);

        int ops = serverChannel.validOps();
        SelectionKey selectKey = serverChannel.register(selector, ops, null);

        // Infinite loop..
        // Keep server running
        while (true) {

            log("i'm a server and i'm waiting for new connection and buffer select...");
            // Selects a set of keys whose corresponding channels are ready for I/O operations
            try {
                if(selector.select() == 0)
                    continue;
            } catch (IOException e) {
                e.printStackTrace();
            }

            // token representing the registration of a SelectableChannel with a Selector
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keysIterator = keys.iterator();

            while (keysIterator.hasNext()) {
                SelectionKey key = keysIterator.next();

                // Tests whether this key's channel is ready to accept a new serverChannel connection
                if (key.isAcceptable()) {
                    SocketChannel smtpClient = serverChannel.accept();

                    // Adjusts this channel's blocking mode to false
                    smtpClient.configureBlocking(false);

                    // Operation-set bit for read operations
                    smtpClient.register(selector, SelectionKey.OP_READ);
                    log("Connection Accepted: " + smtpClient.getLocalAddress() + "\n");

                    // Tests whether this key's channel is ready for reading
                } else if (key.isReadable()) {

                    SocketChannel smtpClient = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    smtpClient.read(buffer);
                    String result = new String(buffer.array()).trim();

                    log("Message received: " + result);

                    if (result.equals("Crunchify")) {
                        smtpClient.close();
                        log("\nIt's time to close connection as we got last company name 'Crunchify'");
                        log("\nServer will keep running. Try running client again to establish new connection");
                    }
                }
                keysIterator.remove();
            }
        }
    }

    private static void log(String str) {
        System.out.println(str);
    }
}
