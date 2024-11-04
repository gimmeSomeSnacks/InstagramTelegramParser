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
            video.setMedia(tempFile, "videoInstagram");
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return video;
    }

    private String getDownloadUrl(String url) {
        String realUrl = "";
        String videoId = url.split("/")[4];
        log.info("id: " + videoId);
        var data = getData(videoId);
        var dataBytesLength = data.getBytes().length;
        var headers = getHeaders();
        headers.put("Content-Length", String.valueOf(dataBytesLength));
        headers.put("Referer", url);

        final var rest = new RestTemplate();
        final var httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::set);
        final var request2 = new HttpEntity<>(data, httpHeaders);
        //ссылка верная на 100%
        final var response2 = rest.postForEntity("https://www.instagram.com/graphql/query", request2, byte[].class);

        long decompressedSize = Zstd.decompressedSize(response2.getBody());
        log.info("decompressedSize: " + decompressedSize);
        byte[] decompressedBytes = new byte[(int) decompressedSize];
        Zstd.decompress(decompressedBytes, response2.getBody());
        log.info("\n\nzstd: " + new String(decompressedBytes, StandardCharsets.UTF_8));
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(new String(decompressedBytes, StandardCharsets.UTF_8));
            realUrl = root.get("data").get("xdt_shortcode_media").get("video_url").asText();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        log.info("realUrl: " + realUrl);
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
//        stringBuilder.append("__spin_t=").append(timestamp);

//        stringBuilder.append("&variables={\"shortcode\":\"").append(videoId).append("\",\"fetch_tagged_user_count\":null,\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}");
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        String data = String.format(stringBuilder.toString(), timestamp, videoId);
        log.info("builder: " + stringBuilder);
        log.info("data: " + data);
        return data;
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
//                log.info("header: " + parts[0] + " " + parts[1]);
                headers.put(parts[0], parts[1]);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return headers;
    }
}



//ByteArrayInputStream bais = new ByteArrayInputStream(response2.getBody());
//        try {
//GZIPInputStream gzis = new GZIPInputStream(bais);
//            log.info("gzip: " + new String(gzis.readAllBytes(), StandardCharsets.UTF_8));
//        gzis.close();
//        } catch (Exception e) {
//        log.error(e.getMessage());
//        }
//        return "av=0&__d=www&__user=0&__a=1&__req=8&__hs=20021.HYP:instagram_web_pkg.2.1..0.0&dpr=1"
//                + "&__ccg=MODERATE&__rev=1017666166&__s=j3951x:0yn8ot:0p8qej&__hsi=7429731714129890552"
//                + "&__dyn=7xeUjG1mxu1syUbFp41twpUnwgU7SbzEdF8aUco2qwJw5ux609vCwjE1xoswaq0yE462mcw5Mx62G5UswoEcE7O2l0Fwqo31w9O1TwQzXwae4UaEW2G0AEco5G0zK5o4q0HUvw5rwSyES1TwVwDwHg2ZwrUdUbGwmk0zU8oC1Iwqo5q3e3zhA6bwIxe6V89F8uwm9EO17wciuEymVUC"
//                + "&__csr=giMF5i8aQBYyqKWqAF6qLnG8JvO7-RyeWQK-KXmVk-nVQqRVkWBoGEgoLlBQ9zElha-9uGryHAVHDzWUKtt4gkyUyAexi9zF948CDxiQ9jx5oTiVUiKcGi5qoLJe4WQcLACCh9UKcxSl1-2mLAzqpkVXx12Fo01e7BKE7h05ewj8fk0fFAwIwLw5Ww1kO0a8CDwJw3CrVhoe86YE3pJ0ho5i0UFpy0hQ8e2JwwSs-tpho5G2Gewf3w9C3l0Kw9K055tzE029Jwdi02r6"
//                + "&__comet_req=7&lsd=AVqN7EKCM8w&jazoest=2860&__spin_r=1017666166&__spin_b=trunk&__spin_t=" + timestamp
//                + "&fb_api_caller_class=RelayModern&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery"
//                + "&variables={\"shortcode\":\"" + videoId + "\",\"fetch_tagged_user_count\":null,\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}"
//                + "&server_timestamps=true&doc_id=8845758582119845";

//        return new String[] {
//                "Accept", "*/*",
//                "Accept-Encoding", "gzip, deflate, br, zstd",
//                "Accept-Language", "ru,en;q=0.9,fr;q=0.8",
//                "Cache-Control", "no-cache",
//                "Content-Type", "application/x-www-form-urlencoded",
//                "Origin", "https://www.instagram.com",
//                "Referer", "https://www.instagram.com/reel/DBRvRvUOpjB/?igsh=dnUwZjBlcXcxaTJ6",
//                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 YaBrowser/24.7.0.0 Safari/537.36",
//                "X-Csrftoken", "V6RzZPOC3TmFFh2sCWgwM5Z5FoTaDdLe"
//        };

//        try (HttpClient httpClient = HttpClient.newHttpClient()) {
//            HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
//                    .uri(URI.create("https://www.instagram.com/api/graphql"))
//                    .version(HttpClient.Version.HTTP_1_1)
//                    .POST(HttpRequest.BodyPublishers.ofString(data));
//            for (Map.Entry<String, String> header : headers.entrySet()) {
//                httpRequest.setHeader(header.getKey(), header.getValue());
//            }
//            HttpRequest request = httpRequest.build();
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//            String responseBody = new String(response.body().getBytes(), StandardCharsets.UTF_8);
//            log.info("response: " + responseBody);
//            ObjectMapper objectMapper = new ObjectMapper();
//            var tree = objectMapper.readTree(responseBody);
//            JsonNode jsonNode = objectMapper.readTree(responseBody);
//            realUrl = objectMapper.readTree(responseBody).get("data").get("xdt_shortcode_media").get("video_url").asText();
//        } catch (Exception e) {
//            log.error(e.getMessage());
//        }