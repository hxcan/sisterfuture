// MessageItem.java
package com.stupidbeauty.sisterfuture.bean;

public class MessageItem {
    public String text;
    private MessageType type;

    public MessageItem(String text, MessageType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public MessageType getType() {
        return type;
    }
}
