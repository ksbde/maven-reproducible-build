package demo;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


/**
 * A simple HTTP server that responds with "Hello, world!" on port 8080.
 * <p>
 * The server listens on {@code http://localhost:8080/hello} and responds
 * with a plain text message.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * java demo.HelloServer
 * }</pre>
 *
 * <p>Once started, open a browser and go to:
 * <a href="http://localhost:8080/hello">http://localhost:8080/hello</a></p>
 */

public class HelloServer {
    /**
     * Starts the HTTP server and serves "Hello, world!" at
     * {@code /hello} endpoint.
     *
     * @param args command-line arguments (not used)
     * @throws IOException if the server fails to start or bind to port 8080
     */
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // Define the /hello endpoint
        server.createContext("/hello", (HttpExchange exchange) -> {
            String response = "Hello, world!";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("Listening on http://localhost:8080/hello");
    }
}
