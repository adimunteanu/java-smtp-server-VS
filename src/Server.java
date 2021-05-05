import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.*;
import java.util.Iterator;
import java.util.Set;

public class Server {
    private static CharsetDecoder decoder = null;
    private static Charset messageCharset = null;
    public static byte[] hostName = null;
    public static byte[] connectionResponse = null;
    public static byte[] okayResponse = null;
    public static byte[] helpResponse = null;
    public static byte[] dataResponse = null;
    public static byte[] quitResponse = null;
    private static byte [] crnlMsg = null;
    private static ByteBuffer buffer;

    public static void initResponses() {
        connectionResponse = new String("220 ").getBytes(messageCharset);
        okayResponse = new String("250 OK").getBytes(messageCharset);
        helpResponse = new String("214 Lest das RFC").getBytes(messageCharset);
        dataResponse = new String("354 Please send the message").getBytes(messageCharset);
        quitResponse = new String("221 Bye").getBytes(messageCharset);
        crnlMsg = new String("\r\n").getBytes(messageCharset);
    }

    public static Command getCommand(SocketChannel channel, ByteBuffer buffer) throws IOException {
        buffer.clear();
        int pos = buffer.position();
        channel.read(buffer);

        byte[] command = new byte[4];
        command[0] = buffer.get(pos);
        command[1] = buffer.get(pos + 1);
        command[2] = buffer.get(pos + 2);
        command[3] = buffer.get(pos + 3);

        switch (new String(command, messageCharset)) {
            case "HELP":
                return Command.HELP;
            case "HELO":
                return Command.HELO;
            case "MAIL":
                return Command.MAIL;
            case "RCPT":
                return Command.RCPT;
            case "DATA":
                return Command.DATA;
            case "QUIT":
                return Command.QUIT;
            default:
                return Command.NONE;
        }
    }

    private static byte[] readAddress(ByteBuffer buffer, int startPos) {
        for (int i = startPos; i < buffer.position(); i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                buffer.flip();
                break;
            }
        }

        byte[] message = new byte[buffer.limit() - startPos - 2];

        for (int i = startPos; i < buffer.limit() - 2; i++) {
            message[i - startPos] = buffer.get(i);
        }

        return message;
    }

    private static byte[] readMessage(ByteBuffer buffer, int startPos) {
        for (int i = startPos; i < buffer.position(); i++) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n' && buffer.get(i + 2) == '.' && buffer.get(i + 3) == '\r' && buffer.get(i + 4) == '\n') {
                buffer.flip();
                break;
            }

            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
                buffer.flip();
                break;
            }
        }

        byte[] message = new byte[buffer.limit() - startPos];

        for (int i = startPos; i < buffer.limit(); i++) {
            message[i - startPos] = buffer.get(i);
        }

        return message;
    }

    public static void sendResponse(SocketChannel channel, ByteBuffer buffer, byte[] response, boolean appendHostName) throws IOException {
        buffer.clear();

        buffer.put(response);
        if (appendHostName) {
            buffer.put(hostName);
        }
        buffer.put(crnlMsg);
        buffer.flip();
        channel.write(buffer);
        buffer.clear();
    }

    private static void printBuffer(ByteBuffer buffer) {

        buffer.position(0);

        CharBuffer cb = null;
        try {
            cb = decoder.decode(buffer);
        } catch (CharacterCodingException e) {
            System.err.println("Cannot show buffer content. Character coding exception...");
            return;
        }

        System.out.println(cb);
    }

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

        try {
            messageCharset = Charset.forName("US-ASCII");
        } catch(UnsupportedCharsetException uce) {
            System.err.println("Cannot create charset for this application. Exiting...");
            System.exit(1);
        }

        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName().getBytes(messageCharset);
        } catch (UnknownHostException e) {
            System.err.println("Cannot determine name of host. Exiting...");
            System.exit(1);
        }

        initResponses();
        decoder = messageCharset.newDecoder();
        buffer = ByteBuffer.allocate(8192);

        log("i'm a server and i'm waiting for new connection and buffer select...");

        // Infinite loop..
        // Keep server running
        while (true) {


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

                    sendResponse(smtpClient, buffer, connectionResponse, true);
                    // Tests whether this key's channel is ready for reading
                } else if (key.isReadable()) {
                    SocketChannel smtpClient = (SocketChannel) key.channel();
                    ClientState state = (ClientState) key.attachment();

                    switch (getCommand(smtpClient, buffer)) {
                        case HELP:
                            sendResponse(smtpClient, buffer, helpResponse, false);
                            break;
                        case HELO:
                            state = new ClientState();
                            state.setMessage_id((int) (Math.random() * 9999));
                            key.attach(state);
                            sendResponse(smtpClient, buffer, okayResponse, false);
                            break;
                        case MAIL:
                            state.setSender(new String(readAddress(buffer, 11), messageCharset));
                            key.attach(state);
                            sendResponse(smtpClient, buffer, okayResponse, false);
                            break;
                        case RCPT:
                            state.setReceiver(new String(readAddress(buffer, 9), messageCharset));
                            key.attach(state);
                            sendResponse(smtpClient, buffer, okayResponse, false);
                            break;
                        case DATA:
                            sendResponse(smtpClient, buffer, dataResponse, false);
                            break;
                        case NONE:
                            String currentMessage = new String(readMessage(buffer, 0), messageCharset);
                            if (currentMessage.endsWith("\r\n.\r\n")) {
                                state.setMessage(state.getMessage() + currentMessage.substring(0, currentMessage.length() - 5));
                                key.attach(state);
                                sendResponse(smtpClient, buffer, okayResponse, false);
                            } else {
                                state.setMessage(state.getMessage() + currentMessage);
                                key.attach(state);
                            }
                            break;
                        case QUIT:
                            IOUtils.createEmailFile(state.getReceiver(), state.getSender(), state.getMessage_id(), state.getMessage());
                            sendResponse(smtpClient, buffer, quitResponse, false);
                            key.cancel();
                            key.channel().close();
                            break;
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
