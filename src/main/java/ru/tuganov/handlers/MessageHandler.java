package ru.tuganov.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHandler {

    private final ResourceLoader resourceLoader;

    public SendMediaGroup handleUrls(Update update) {
        var message = update.getMessage();
        var chatId = message.getChatId();
        var urls = message.getText();
        List<String> urlList = new ArrayList<>(List.of(urls.split("[ \\n\\r]")));
        List<InputMedia> mediaList = new ArrayList<>();
        SendMediaGroup sendMediaGroup = new SendMediaGroup();
        sendMediaGroup.setChatId(chatId);
        for (String url : urlList) {
            mediaList.add(getVideo(url));
        }
        sendMediaGroup.setMedias(mediaList);
        return sendMediaGroup;
    }

    private InputMediaVideo getVideo(String url) {
        InputMediaVideo video = new InputMediaVideo();
        String realUrl = getDownloadUrl(url);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var headers = getHeaders();
            HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(realUrl));
            for (Map.Entry<String, String> header : headers.entrySet()) {
                httpRequest.setHeader(header.getKey(), header.getValue());
            }
            HttpRequest request = httpRequest.build();
            HttpResponse<InputStream> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            File tempFile = File.createTempFile("video", ".mp4");
            try (InputStream inputStream = httpResponse.body();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            String generatedString = new String(array, StandardCharsets.UTF_8);
            video.setMedia(tempFile, generatedString);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return video;
    }

    private String getDownloadUrl(String url) {
        String realUrl = "";
        String videoId = url.split("/")[4];
        var data = getData(videoId);
        var dataBytesLength = data.getBytes().length;
        var headers = getHeaders();
        headers.put("Content-Length", String.valueOf(dataBytesLength));
        headers.put("Referer", url);

        final var rest = new RestTemplate();
        final var httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::set);
        final var request2 = new HttpEntity<>(data, httpHeaders);
        final var response2 = rest.postForEntity("https://www.instagram.com/graphql/query", request2, byte[].class);

        long decompressedSize = Zstd.decompressedSize(response2.getBody());
        byte[] decompressedBytes = new byte[(int) decompressedSize];
        Zstd.decompress(decompressedBytes, response2.getBody());
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(new String(decompressedBytes, StandardCharsets.UTF_8));
            realUrl = root.get("data").get("xdt_shortcode_media").get("video_url").asText();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return realUrl;
    }

    private String getData(String videoId) {
        long timestamp = Instant.now().getEpochSecond();
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(
            new InputStreamReader(resourceLoader.getResource("classpath:static/data.txt").getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] parts = line.split(" ");
                parts[0] = parts[0].replace(":", "=");
                stringBuilder.append(parts[0]).append(parts[1]).append("&");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        return String.format(stringBuilder.toString(), timestamp, videoId);
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(resourceLoader.getResource("classpath:static/headers.txt").getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                parts[0] = parts[0].replace(":", "");
                headers.put(parts[0], parts[1]);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return headers;
    }
}