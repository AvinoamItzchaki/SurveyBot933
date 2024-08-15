package org.apache;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        //"SurveyBot933"
        //"7309326316:AAGtI9vN9SG6HuFyxK1uhVbc5hQLDWhDjhk"

        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            SurveyBot surveyBot = new SurveyBot();
            telegramBotsApi.registerBot(surveyBot);
            surveyBot.sendStartMessageToAllUsers();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}