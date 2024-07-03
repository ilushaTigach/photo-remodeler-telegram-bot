package org.telatenko.photoremodelertelegrambot.Bot;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telatenko.photoremodelertelegrambot.botConfig.BotConfig;
import org.telatenko.photoremodelertelegrambot.services.BotService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

@Slf4j
@Component
public class MenuImageCaptionBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(MenuImageCaptionBot.class);

    @Autowired
    private final BotConfig botConfig;

    @Autowired
    private final BotService botService;

    public MenuImageCaptionBot(BotConfig botConfig, BotService botService) {
        this.botConfig = botConfig;
        this.botService = botService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendWelcomeMessage(chatId);
            } else if (messageText.equals("1") || messageText.equals("2") || messageText.equals("3")) {
                handleOption(chatId, Integer.parseInt(messageText));
            }
        } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            Random random = new Random();
            logger.info("Received photo message");
            String chatId = update.getMessage().getChatId().toString();
            String caption = update.getMessage().getCaption();
            if (caption == null) {
                String[] names = {"земеля", "пихота", "минёха", "чич ", "тубик"};
                int randomIndexNames = random.nextInt(names.length);
                caption = names[randomIndexNames];
            }

            PhotoSize photo = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1);
            try {
                File file = botService.downloadPhoto(photo.getFileId(), this);
                BufferedImage image = ImageIO.read(file);
                processImage(chatId, image, caption);
            } catch (IOException | TelegramApiException e) {
                logger.error("Error processing photo", e);
            }
        }
    }

    private void sendWelcomeMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Привет! Выберите опцию:\n1) Уменьшить качество и растянуть\n2) Уменьшить качество, растянуть и добавить надпись\n3) Добавить надпись");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending welcome message", e);
        }
    }

    private int currentOption = 0;

    private void handleOption(long chatId, int option) {
        currentOption = option;
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выбрана опция " + option + ". Пришлите фото.");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error handling option " + option, e);
        }
    }

    private void processImage(String chatId, BufferedImage image, String caption) throws IOException, TelegramApiException {
        switch (currentOption) {
            case 1:
                BufferedImage resizedImage = botService.resizeImage(image, image.getWidth() * 2, image.getHeight());
                BufferedImage lowQualityImage = botService.reduceImageQuality(resizedImage, 0.005f); // Уменьшаем качество
                botService.sendPhoto(chatId, lowQualityImage, this);
                break;
            case 2:
                resizedImage = botService.resizeImage(image, image.getWidth() * 2, image.getHeight());
                lowQualityImage = botService.reduceImageQuality(resizedImage, 0.005f); // Уменьшаем качество
                BufferedImage captionedImage = botService.addTextToImage(lowQualityImage, caption);
                botService.sendPhoto(chatId, captionedImage, this);
                break;
            case 3:
                captionedImage = botService.addTextToImage(image, caption);
                botService.sendPhoto(chatId, captionedImage, this);
                break;
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }
}