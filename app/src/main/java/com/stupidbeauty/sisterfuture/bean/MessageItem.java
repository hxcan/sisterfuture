// MessageItem.java
package com.stupidbeauty.sisterfuture.bean;

public class MessageItem {
    public String text;
    private MessageType type;
    public boolean isExpanded; // #4794: Control display state

    public MessageItem(String text, MessageType type) {
        this.text = text;
        this.type = type;
        this.isExpanded = false; // Default: collapsed
    }

    public String getText() {
        return text;
    }

    public MessageType getType() {
        return type;
    }
}
