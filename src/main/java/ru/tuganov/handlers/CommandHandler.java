package ru.tuganov.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.tuganov.Message;

@Slf4j
@Service
public class CommandHandler {

    public SendMessage handleCommands(Update update) {
        var message = update.getMessage();
        var chatId = message.getChatId();
        return new SendMessage(String.valueOf(chatId), Message.help);
    }
}
