package org.apache;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.*;

public class SurveyBot extends TelegramLongPollingBot {

    private Map<Long, List<String>> communityMembers = new HashMap<>();
    //private Map<Long, Survey> activeSurveys = new HashMap<>();
    private static final String FILE_PATH = "TelegramBot\\src\\main\\java\\org\\apache\\chatIds.txt";
    private Set<Long> userIds = new HashSet<>();
    private Map<Long, Boolean>have_we_already_sent_a_start_message = new HashMap<>();

    private Map<Long, List<Survey>> ongoingSurveys = new HashMap<>();
    private Map<Long, Integer> stepTracker = new HashMap<>(); // עקוב אחר שלב השיח עם כל משתמש
    private Map<Long, List<String>> questions = new HashMap<>(); // שאלות זמניות עבור המשתמש
    private Map<Long, List<List<String>>> options = new HashMap<>(); // אפשרויות זמניות עבור כל שאלה


    private boolean allMembersResponded;
    private Long creatorChatId;

    public SurveyBot() {
        loadChatIds();
        for (Long userId: userIds){
            have_we_already_sent_a_start_message.put(userId,false);
        }
    }

    @Override
    public String getBotUsername() {
        return "SurveyBot933";
    }

    @Override
    public String getBotToken() {
        return "7309326316:AAGtI9vN9SG6HuFyxK1uhVbc5hQLDWhDjhk";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            if (!userIds.contains(chatId)) {
                saveChatId(chatId);
                userIds.add(chatId);
                have_we_already_sent_a_start_message.put(chatId,false);
            }

            if (!have_we_already_sent_a_start_message.get(chatId)) {
                sendStartMessage(chatId);
            }

            if (message.getText().equalsIgnoreCase("/start") || message.getText().equalsIgnoreCase("היי") || message.getText().equalsIgnoreCase("Hi")) {
                addUserToCommunity(chatId);
            } else if (message.getText().startsWith("/create_survey")) {
                boolean hasSurveys = ongoingSurveys.values().stream()
                        .anyMatch(surveys -> !surveys.isEmpty());
                if (communityMembers.size() < 3) {
                    sendMessage(chatId, "לא ניתן ליצור סקר, נדרשים לפחות 3 חברים בקהילה.");
                    return;
                }
                else if (hasSurveys) {
                    sendMessage(chatId, "כבר קיים סקר פעיל, יש להמתין לסיומו.");
                    return;
                }
                else{
                    stepTracker.put(chatId, 1);
                    sendMessage(chatId, "בבקשה תכתוב את השאלה הראשונה שלך לסקר (או 'סיום' כדי לסיים):");
                    questions.put(chatId, new ArrayList<>());
                    options.put(chatId, new ArrayList<>());
                }
            }else {
                handleSurveyCreation(chatId, message.getText());
            }
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            String callbackData = callbackQuery.getData();
            if ("join_community".equals(callbackData)) {
                Long chatId = callbackQuery.getMessage().getChatId();
                addUserToCommunity(chatId);
            } else {
                long chatId = callbackQuery.getMessage().getChatId();
                handleSurveyResponse(chatId,callbackQuery);
            }

        }
    }
    private void saveChatId(Long chatId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            writer.write(chatId.toString());
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadChatIds() {
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                userIds.add(Long.parseLong(line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendStartMessageToAllUsers() {
        for (Long chatId : userIds) {
            sendStartMessage(chatId);
        }
    }

    public void sendStartMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("לחץ על START כדי להצטרף לקהילה:");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("start");
        button.setCallbackData("join_community");
        row.add(button);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        sendMessage(chatId,"תוכל גם להצטרף לקהילה על ידי שליחת הודעה Hi או היי או /start");

        have_we_already_sent_a_start_message.put(chatId,true);
    }


    private void addUserToCommunity(Long chatId) {
        if (!communityMembers.containsKey(chatId)) {
            communityMembers.put(chatId, new ArrayList<>());
            sendMessage(chatId, "ברוך הבא לקהילה! כעת אתה חבר בקהילה.");
            broadcastMessage("חבר חדש הצטרף לקהילה! כעת יש " + communityMembers.size() + " חברים בקהילה.");
            sendMessage(chatId, "/create_survey" +" אם תרצה ליצור סקר תשלח את ההודעה: ");
        }
    }


    private void handleSurveyCreation(Long chatId, String message) {
        int step = stepTracker.get(chatId);
        //stepTracker.put(chatId, step); // הבטחת שמירת השלב הנוכחי למשתמש
        if (message.equalsIgnoreCase("סיום")) {
            sendMessage(chatId, "האם תרצה להמתין לפני שליחת הסקר? כתוב 'כן' או 'לא'.");
            stepTracker.put(chatId, 7);
            return;
        }

        switch (step) {
            case 1:
            case 3:
            case 5:
                questions.get(chatId).add(message);
                sendMessage(chatId, "בבקשה תכתוב בין 2 ל-4 אפשרויות עבור השאלה, מופרדות בפסיקים:");
                stepTracker.put(chatId, step + 1);
                break;
            case 2:
            case 4:
            case 6:
                List<String> a = Arrays.asList(message.split(","));
                if (a.size() >= 2 && a.size() <= 4) {
                    Collections.reverse(a);
                    options.get(chatId).add(a);
                } else {
                    sendMessage(chatId, "יש לבחור בין 2 ל-4 אפשרויות תשובה.");
                    sendMessage(chatId, "בבקשה תכתוב בין 2 ל-4 אפשרויות עבור השאלה, מופרדות בפסיקים:");
                    stepTracker.put(chatId, step);
                    return;
                }
                if (step == 6 || questions.get(chatId).size() == 3) {
                    sendMessage(chatId, "האם תרצה להמתין לפני שליחת הסקר? כתוב 'כן' או 'לא'.");
                    stepTracker.put(chatId, 7);
                } else {
                    sendMessage(chatId, "בבקשה תכתוב את השאלה הבאה שלך לסקר (או 'סיום' כדי לסיים):");
                    stepTracker.put(chatId, step + 1);
                }
                break;
            case 7:
                if (message.equalsIgnoreCase("כן")) {
                    sendMessage(chatId, "כמה דקות תרצה להמתין?");
                    stepTracker.put(chatId, 8);
                } else if (message.equalsIgnoreCase("לא")) {
                    finalizeSurveys(chatId, 0);
                } else {
                    sendMessage(chatId, "אנא כתוב 'כן' או 'לא'.");
                }
                break;
            case 8:
                try {
                    int delayMinutes = Integer.parseInt(message);
                    finalizeSurveys(chatId, delayMinutes);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "פורמט לא נכון. אנא הכנס מספר תקין של דקות.");
                }
                break;
        }
    }

    private void finalizeSurveys(Long chatId, int delayMinutes) {
        List<Survey> surveys = new ArrayList<>();
        List<String> userQuestions = questions.get(chatId);
        List<List<String>> userOptions = options.get(chatId);

        for (int i = 0; i < userQuestions.size(); i++) {
            surveys.add(new Survey(userQuestions.get(i), userOptions.get(i), chatId, i));
        }

        ongoingSurveys.put(chatId, surveys);
        sendMessage(chatId, "הסקר שלך נוצר בהצלחה!");
        for (Survey survey : surveys) {
            survey.setDelay(delayMinutes);
        }
        this.creatorChatId = chatId;

        for (Survey survey : surveys) {
            if (survey.getDelay() > 0) {
                sendMessage(chatId, "הסקר ישלח לאחר " + survey.getDelay() + " דקות.");
                scheduleSurveyBroadcast(survey, survey.getDelay(), survey.getOrderSurvey());
            } else {
                survey.setStartTime(new Date());
                sendSurveyToTheCrowd(survey, survey.getOrderSurvey());
                checkIfTheSurveyTimesOut(survey);
            }
        }

        stepTracker.remove(chatId);
        questions.remove(chatId);
        options.remove(chatId);
    }

    private void scheduleSurveyBroadcast(Survey survey, int delayMinutes, int orderSurvey) {
        new Thread(() -> {
            try {
                // המתנה למשך הזמן המבוקש
                Thread.sleep((long) delayMinutes * 60 * 1000);
                // ביצוע הפעולות לאחר ההמתנה
                survey.setStartTime(new Date());
                sendSurveyToTheCrowd(survey, orderSurvey);
                checkIfTheSurveyTimesOut(survey);
            } catch (InterruptedException e) {
                e.printStackTrace();
                // במידת הצורך, אפשר להוסיף טיפול בשגיאות כאן
            }
        }).start();
    }




    private void sendSurveyToTheCrowd(Survey survey, int orderSurvey) {
        SendMessage message = new SendMessage();
        message.setText(survey.getQuestion());

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton option1 = new InlineKeyboardButton();
        InlineKeyboardButton option2 = new InlineKeyboardButton();
        InlineKeyboardButton option3 = new InlineKeyboardButton();
        InlineKeyboardButton option4 = new InlineKeyboardButton();

        if (survey.getOptions().size() == 2){
            option1.setText(survey.getOptions().getFirst());
            option1.setCallbackData(orderSurvey + "1");
            option2.setText(survey.getOptions().get(1));
            option2.setCallbackData(orderSurvey + "2");

            row.add(option1);
            row.add(option2);
        } else if (survey.getOptions().size() == 3) {
            option1.setText(survey.getOptions().getFirst());
            option1.setCallbackData(orderSurvey + "1");
            option2.setText(survey.getOptions().get(1));
            option2.setCallbackData(orderSurvey + "2");
            option3.setText(survey.getOptions().get(2));
            option3.setCallbackData(orderSurvey + "3");

            row.add(option1);
            row.add(option2);
            row.add(option3);
        }else if (survey.getOptions().size() == 4) {
            option1.setText(survey.getOptions().getFirst());
            option1.setCallbackData(orderSurvey + "1");
            option2.setText(survey.getOptions().get(1));
            option2.setCallbackData(orderSurvey + "2");
            option3.setText(survey.getOptions().get(2));
            option3.setCallbackData(orderSurvey + "3");
            option4.setText(survey.getOptions().get(3));
            option4.setCallbackData(orderSurvey + "4");

            row.add(option1);
            row.add(option2);
            row.add(option3);
            row.add(option4);
        }

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        for (Long memberId : communityMembers.keySet()) {
            message.setChatId(memberId.toString());
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkIfTheSurveyTimesOut(Survey survey) {
        Thread thread = new Thread(() -> {
            while (!survey.allMembersResponded(communityMembers.size())) {
                if (survey.isPastSurveyTime()) {
                    synchronized (survey) {
                        if (!survey.isClosed()) { // בדיקה אם הסקר נסגר כבר
                            survey.setClosed(true); // סימון שהסקר נסגר
                            broadcastMessage("זמן הסקר עבר!");
                            sendSurveyResults(survey);
                            sendSurveyResultsToCreator(survey);
                            ongoingSurveys.get(creatorChatId).remove(survey);
                            this.creatorChatId = 0L;
                        }
                    }
                    break;
                }
            }
        });
        thread.start();
    }

    private void handleSurveyResponse(Long chatId, CallbackQuery callbackQuery) {
        List<Survey> surveys1 = ongoingSurveys.get(creatorChatId);
        System.out.println(callbackQuery.getData());
        for (Survey survey : surveys1) {
            if (survey.getOrderSurvey() == Character.getNumericValue(callbackQuery.getData().charAt(0))) {
                if (!survey.hasResponded(chatId)) {
                   System.out.println(callbackQuery.getData());
                   switchIntoOptions(chatId,callbackQuery,survey);
                } else {
                    sendMessage(chatId, "כבר ענית על הסקר הזה.");
                }

                synchronized (survey) {
                    if (survey.allMembersResponded(communityMembers.size()) && !survey.isClosed()) {
                        survey.setClosed(true); // סימון שהסקר נסגר
                        sendSurveyResults(survey); // שליחת תוצאות הסקר
                        sendSurveyResultsToCreator(survey);
                        ongoingSurveys.get(creatorChatId).remove(survey); // סיום הסקר והסרתו מהרשימה
                        break; // יציאה מהלולאה כדי למנוע המשך טיפול בסקר שהוסר
                    }
                }
            }
        }

    }

    private void switchIntoOptions(Long chatId, CallbackQuery callbackQuery, Survey survey){
        int size = survey.getOptions().size();
        System.out.println(callbackQuery.getData());
        if (size == 2){
            switch (callbackQuery.getData().charAt(1)){
                case '1':
                    survey.recordResponse(chatId, survey.getOptions().get(0));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(0));
                    break;

                case '2':
                    survey.recordResponse(chatId, survey.getOptions().get(1));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(1));
                    break;

            }

        }else if (size == 3){
            switch (callbackQuery.getData().charAt(1)){
                case '1':
                    survey.recordResponse(chatId, survey.getOptions().get(0));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(0));
                    break;

                case '2':
                    survey.recordResponse(chatId, survey.getOptions().get(1));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(1));
                    break;

                case '3':
                    survey.recordResponse(chatId, survey.getOptions().get(2));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(2));
                    break;

            }
        }else if (size == 4){
            switch (callbackQuery.getData().charAt(1)){
                case '1':
                    survey.recordResponse(chatId, survey.getOptions().get(0));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(0));
                    break;

                case '2':
                    survey.recordResponse(chatId, survey.getOptions().get(1));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(1));
                    break;

                case '3':
                    survey.recordResponse(chatId, survey.getOptions().get(2));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId, "ענית: " + survey.getOptions().get(2));
                    break;

                case '4':
                    survey.recordResponse(chatId, survey.getOptions().get(3));
                    sendMessage(chatId, "תשובתך התקבלה!");
                    sendMessage(chatId,  "ענית: " + survey.getOptions().get(3));
                    break;
            }
        }

    }

    private void sendSurveyResults(Survey survey) {
        String results = survey.getResultsAsString();
        broadcastMessage("תוצאות הסקר: " + "\n" + survey.getQuestion() + "\n" +  results);
    }

    private void broadcastMessage(String text) {
        for (Long memberId : communityMembers.keySet()) {
            sendMessage(memberId, text);
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void checkAndCleanUpSurveys() {
        Iterator<Map.Entry<Long, List<Survey>>> iterator = ongoingSurveys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, List<Survey>> entry = iterator.next();
            Survey survey = (Survey) entry.getValue();
            // אם הזמן לסקר עבר או שכל המשתתפים ענו, ננקה את הסקר
            if (survey.isPastSurveyTime() || survey.allMembersResponded(communityMembers.size())) {
                iterator.remove();
            }
        }
    }
    private void sendSurveyResultsToCreator(Survey survey) {
        Long creatorId = survey.getCreatorId();
        Map<String, Integer> responses = survey.getResults();
        int totalVotes = responses.values().stream().mapToInt(Integer::intValue).sum();

        // יצירת רשימה של אפשרויות עם אחוזי ההצבעה וסידורן בסדר יורד לפי אחוז ההצבעה
        List<Map.Entry<String, Double>> sortedResults = responses.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), (double) entry.getValue() / totalVotes * 100))
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))  // סידור בסדר יורד לפי אחוזים
                .toList();

        // בניית ההודעה עם התוצאות
        StringBuilder resultMessage = new StringBuilder("תוצאות הסקר באחוזים :\n");
        resultMessage.append(survey.getQuestion() + "\n");
        for (Map.Entry<String, Double> entry : sortedResults) {
            String option = entry.getKey();
            double percentage = entry.getValue();
            int voteCount = responses.get(option);
            resultMessage.append(String.format("%s: %.2f%% (%d הצבעות)\n", option, percentage, voteCount));
        }

        // שליחת ההודעה ליוצר הסקר
        sendMessage(creatorId, resultMessage.toString());
    }
}


