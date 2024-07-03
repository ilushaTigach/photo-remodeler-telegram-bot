package org.telatenko.photoremodelertelegrambot.Bot;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telatenko.photoremodelertelegrambot.botConfig.BotConfig;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class ImageCaptionBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(ImageCaptionBot.class);

    @Autowired
    private final BotConfig botConfig;

    public ImageCaptionBot(BotConfig botConfig) {
        this.botConfig = botConfig;
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
            logger.info("Received photo message");
            String chatId = update.getMessage().getChatId().toString();
            String caption = update.getMessage().getCaption();
            if (caption == null) {
                caption = "земеля";
            }

            PhotoSize photo = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1);
            try {
                File file = downloadPhoto(photo.getFileId());
                BufferedImage image = ImageIO.read(file);
                processImage(chatId, image, caption);
            } catch (IOException | TelegramApiException e) {
                logger.error("Error processing photo", e);
            }
        }
    }

    private int currentOption = 0;

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
                BufferedImage resizedImage = resizeImage(image, image.getWidth() * 2, image.getHeight());
                BufferedImage lowQualityImage = reduceImageQuality(resizedImage, 0.005f); // Уменьшаем качество
                sendPhoto(chatId, lowQualityImage);
                break;
            case 2:
                resizedImage = resizeImage(image, image.getWidth() * 2, image.getHeight());
                lowQualityImage = reduceImageQuality(resizedImage, 0.005f); // Уменьшаем качество
                BufferedImage captionedImage = addTextToImage(lowQualityImage, caption);
                sendPhoto(chatId, captionedImage);
                break;
            case 3:
                captionedImage = addTextToImage(image, caption);
                sendPhoto(chatId, captionedImage);
                break;
        }
    }

    private BufferedImage addTextToImage(BufferedImage image, String text) {
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        //Определяем размеры текста
        g2d.setFont(new Font("Arial", Font.BOLD, 50));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        //Определяем координаты для текста
        int x = (image.getWidth() - textWidth) / 2;
        int y = image.getHeight() - textHeight;

        //Рисуем черный фон для текста
        g2d.setColor(Color.BLACK);
        g2d.fillRect(x, y - textHeight + 10, textWidth, textHeight);

        //Рисуем белый текст
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, y);

        g2d.dispose();
        return image;
    }

    private void sendPhoto(String chatId, BufferedImage image) throws IOException, TelegramApiException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(is, "captioned_image.jpg"));
        logger.info("Sending photo to chatId: {}", chatId);
        execute(sendPhoto);
    }

    private File downloadPhoto(String fileId) throws TelegramApiException {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
        return downloadFile(file);
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private BufferedImage reduceImageQuality(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();

        return ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
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