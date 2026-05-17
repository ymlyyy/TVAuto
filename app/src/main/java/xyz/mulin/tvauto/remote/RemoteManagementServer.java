package xyz.mulin.tvauto.remote;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import xyz.mulin.tvauto.data.ChannelRepository;
import xyz.mulin.tvauto.data.UserScriptRepository;
import xyz.mulin.tvauto.model.Channel;
import xyz.mulin.tvauto.model.UserScript;

public final class RemoteManagementServer extends Thread {
    private final ServerSocket serverSocket;
    private final AssetManager assetManager;
    private final ChannelRepository repository;
    private final UserScriptRepository userScriptRepository;
    private final String defaultChannelsText;
    private final Runnable onChannelsChanged;
    private volatile boolean running = true;

    public RemoteManagementServer(
            AssetManager assetManager,
            ChannelRepository repository,
            UserScriptRepository userScriptRepository,
            String defaultChannelsText,
            Runnable onChannelsChanged
    ) throws Exception {
        this.serverSocket = new ServerSocket(0);
        this.assetManager = assetManager;
        this.repository = repository;
        this.userScriptRepository = userScriptRepository;
        this.defaultChannelsText = defaultChannelsText;
        this.onChannelsChanged = onChannelsChanged;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handleClient(socket);
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket socket) {
        try (
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream()
        ) {
            HttpRequest request = readRequest(input);
            if (request == null) return;

            if ("GET".equals(request.method) && "/".equals(request.path)) {
                writeTextResponse(output, 200, "text/html; charset=UTF-8", readAsset("remote/index.html"));
                return;
            }
            if ("GET".equals(request.method) && "/app.css".equals(request.path)) {
                writeTextResponse(output, 200, "text/css; charset=UTF-8", readAsset("remote/app.css"));
                return;
            }
            if ("GET".equals(request.method) && "/app.js".equals(request.path)) {
                writeTextResponse(output, 200, "application/javascript; charset=UTF-8", readAsset("remote/app.js"));
                return;
            }
            if ("GET".equals(request.method) && "/api/channels".equals(request.path)) {
                writeJsonResponse(output, channelsJson());
                return;
            }
            if ("GET".equals(request.method) && "/api/scripts".equals(request.path)) {
                writeJsonResponse(output, scriptsJson());
                return;
            }
            if ("GET".equals(request.method) && "/export.txt".equals(request.path)) {
                writeDownloadResponse(
                        output,
                        repository.exportAsText(),
                        "text/plain; charset=UTF-8",
                        datedFilename("tvauto-channels", "txt")
                );
                return;
            }
            if ("GET".equals(request.method) && "/scripts.json".equals(request.path)) {
                writeDownloadResponse(
                        output,
                        userScriptRepository.exportAsJson(),
                        "application/json; charset=UTF-8",
                        datedFilename("tvauto-scripts", "json")
                );
                return;
            }
            if ("POST".equals(request.method) && "/api/channels".equals(request.path)) {
                JSONObject body = new JSONObject(request.bodyAsText());
                boolean added = repository.addChannel(new Channel(body.getString("name"), body.getString("url")));
                if (added) onChannelsChanged.run();
                writeJsonResponse(output, new JSONObject().put("added", added));
                return;
            }
            if ("POST".equals(request.method) && "/api/import".equals(request.path)) {
                int imported = repository.importChannels(request.bodyAsText());
                if (imported > 0) onChannelsChanged.run();
                writeJsonResponse(output, new JSONObject().put("imported", imported));
                return;
            }
            if ("POST".equals(request.method) && "/api/delete".equals(request.path)) {
                JSONObject body = new JSONObject(request.bodyAsText());
                boolean deleted = repository.deleteByUrl(body.getString("url"));
                if (deleted) onChannelsChanged.run();
                writeJsonResponse(output, new JSONObject().put("deleted", deleted));
                return;
            }
            if ("POST".equals(request.method) && "/api/clear".equals(request.path)) {
                int deleted = repository.deleteAll();
                if (deleted > 0) onChannelsChanged.run();
                writeJsonResponse(output, new JSONObject().put("deleted", deleted));
                return;
            }
            if ("POST".equals(request.method) && "/api/defaults".equals(request.path)) {
                int restored = repository.restoreDefaults(defaultChannelsText);
                if (restored > 0) onChannelsChanged.run();
                writeJsonResponse(output, new JSONObject().put("restored", restored));
                return;
            }
            if ("POST".equals(request.method) && "/api/scripts".equals(request.path)) {
                JSONObject body = new JSONObject(request.bodyAsText());
                userScriptRepository.upsert(
                        body.getString("sitePattern"),
                        body.getString("javascript")
                );
                writeJsonResponse(output, new JSONObject().put("saved", true));
                return;
            }
            if ("POST".equals(request.method) && "/api/scripts/delete".equals(request.path)) {
                JSONObject body = new JSONObject(request.bodyAsText());
                boolean deleted = userScriptRepository.deleteByPattern(body.getString("sitePattern"));
                writeJsonResponse(output, new JSONObject().put("deleted", deleted));
                return;
            }
            if ("POST".equals(request.method) && "/api/scripts/import".equals(request.path)) {
                UserScriptRepository.ImportResult result =
                        userScriptRepository.importFromJson(request.bodyAsText());
                writeJsonResponse(output, new JSONObject()
                        .put("added", result.getAdded())
                        .put("updated", result.getUpdated()));
                return;
            }

            writeTextResponse(output, 404, "text/plain; charset=UTF-8", "Not found");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private JSONObject channelsJson() throws Exception {
        JSONArray array = new JSONArray();
        List<Channel> channels = repository.loadUserChannels();
        for (Channel channel : channels) {
            array.put(new JSONObject()
                    .put("name", channel.getName())
                    .put("url", channel.getUrl()));
        }
        return new JSONObject().put("channels", array);
    }

    private JSONObject scriptsJson() throws Exception {
        JSONArray array = new JSONArray();
        List<UserScript> scripts = userScriptRepository.loadAll();
        for (UserScript script : scripts) {
            array.put(new JSONObject()
                    .put("sitePattern", script.getSitePattern())
                    .put("javascript", script.getJavascript()));
        }
        return new JSONObject().put("scripts", array);
    }

    private String readAsset(String path) throws Exception {
        try (InputStream input = assetManager.open(path)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private HttpRequest readRequest(InputStream input) throws Exception {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int matched = 0;
        int current;
        while ((current = input.read()) != -1) {
            headerBytes.write(current);
            if ((matched == 0 || matched == 2) && current == '\r') matched++;
            else if ((matched == 1 || matched == 3) && current == '\n') matched++;
            else matched = 0;
            if (matched == 4) break;
        }
        if (headerBytes.size() == 0) return null;

        String headersText = headerBytes.toString(StandardCharsets.ISO_8859_1.name());
        String[] lines = headersText.split("\\r\\n");
        String[] requestLine = lines[0].split(" ");
        String method = requestLine[0];
        String rawPath = requestLine[1];
        String path = rawPath.contains("?") ? rawPath.substring(0, rawPath.indexOf('?')) : rawPath;

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            headers.put(
                    line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim()
            );
        }

        String contentLengthHeader = headers.get("content-length");
        int contentLength = Integer.parseInt(contentLengthHeader != null ? contentLengthHeader : "0");
        byte[] body = new byte[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int read = input.read(body, totalRead, contentLength - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        if (totalRead < contentLength) {
            byte[] truncated = new byte[totalRead];
            System.arraycopy(body, 0, truncated, 0, totalRead);
            body = truncated;
        }
        return new HttpRequest(method, URLDecoder.decode(path, StandardCharsets.UTF_8.name()), body);
    }

    private void writeJsonResponse(OutputStream output, JSONObject body) throws Exception {
        writeTextResponse(output, 200, "application/json; charset=UTF-8", body.toString());
    }

    private String datedFilename(String prefix, String extension) {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        return prefix + "-" + date + "." + extension;
    }

    private void writeDownloadResponse(
            OutputStream output,
            String body,
            String contentType,
            String filename
    ) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Disposition: attachment; filename=\"" + filename + "\"\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private void writeTextResponse(OutputStream output, int code, String contentType, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Not Found") + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.flush();
    }

    private static final class HttpRequest {
        final String method;
        final String path;
        final byte[] body;

        HttpRequest(String method, String path, byte[] body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }

        String bodyAsText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
