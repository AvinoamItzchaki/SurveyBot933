package org.apache;
import java.util.*;

public class Survey {
    private String question;
    private List<String> options;
    private Map<Long, String> responses = new HashMap<>();
    private Date startTime;
    private Long creatorId;
    private boolean surveyActive;
    private boolean closed;
    private int delay = 0;
    private int orderSurvey;


    public Survey(String question, List<String> options, Long creatorId, int orderSurvey) {
        this.question = question;
        this.options = options;
        this.creatorId = creatorId;
        this.surveyActive = true;
        this.orderSurvey  = orderSurvey;
    }

    public void recordResponse(Long chatId, String response) {
        responses.put(chatId, response);
    }

    public boolean hasResponded(Long chatId) {
        return responses.containsKey(chatId);
    }

    public boolean isSurveyActive() {
        return surveyActive;
    }

    public boolean isPastSurveyTime() {
        return new Date().getTime() - startTime.getTime() > 5 * 60 * 1000;

    }

    public boolean allMembersResponded(int totalMembers) {
        return responses.size() == totalMembers;
    }

    public String getResultsAsString() {
        Map<String, Integer> resultsCount = getResults();

        StringBuilder results = new StringBuilder();
        resultsCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> results.append(entry.getKey()).append(": ").append(entry.getValue()).append(" הצבעות\n"));
        return results.toString();
    }
    public Map<String, Integer> getResults() {
        Map<String, Integer> resultsCount = new HashMap<>();
        for (String option : options) {
            resultsCount.put(option, 0);
        }
        for (String response : responses.values()) {
            resultsCount.put(response, resultsCount.get(response) + 1);
        }

        return resultsCount;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public Map<Long, String> getResponses() {
        return responses;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public Date getStartTime() {
        return startTime;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getOrderSurvey() {
        return orderSurvey;
    }

    public void setOrderSurvey(int orderSurvey) {
        this.orderSurvey = orderSurvey;
    }
}
