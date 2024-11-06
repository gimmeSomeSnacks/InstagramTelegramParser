package ru.tuganov;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import ru.tuganov.handlers.CommandHandler;
import ru.tuganov.handlers.MessageHandler;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TGBot extends TelegramLongPollingBot {
    @Value("${bot.name}")
    private String botName;
    @Getter
    @Value("${bot.token}")
    private String botToken;
    private final MessageHandler messageHandler;
    private final CommandHandler commandHandler;

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().startsWith("/")) {
                sendMessage(commandHandler.handleCommands(update));
            } else {
                sendVideos(messageHandler.handleUrls(update));
            }
        }
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void sendVideo(SendVideo video) {
        try {
            execute(video);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void sendVideos(SendMediaGroup sendMediaGroup) {
        if (sendMediaGroup.getMedias().isEmpty()) {
            sendMessage(new SendMessage(sendMediaGroup.getChatId(), Message.noUrls));
        } else if (sendMediaGroup.getMedias().size() == 1) {
            SendVideo sendVideo = new SendVideo(sendMediaGroup.getChatId(), new InputFile(sendMediaGroup.getMedias().getFirst().getNewMediaFile()));
            sendVideo.setCaption(Message.returnVideos);
            sendVideo(sendVideo);
        } else if (sendMediaGroup.getMedias().size() > 10) {
            final List<InputMedia> videos = new ArrayList<>();
            for (int i = 0; i < sendMediaGroup.getMedias().size(); i++) {
                if (i % 10 == 0) {
                    SendMediaGroup sendMedia = new SendMediaGroup(sendMediaGroup.getChatId(), videos);
                    sendMedias(sendMedia);
                    videos.clear();
                }
                videos.add(sendMediaGroup.getMedias().get(i));
            }
            if (!videos.isEmpty()) {
                SendMediaGroup sendMedia = new SendMediaGroup(sendMediaGroup.getChatId(), videos);
                sendMedias(sendMedia);
                videos.clear();
            }
        } else {
            sendMedias(sendMediaGroup);
        }
    }

    private void sendMedias(SendMediaGroup sendMediaGroup) {
        try {
            sendMediaGroup.getMedias().getFirst().setCaption(Message.returnVideos);
            execute(sendMediaGroup);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botName;
    }
}
