package com.modernchat.common;

import com.modernchat.ModernChatConfig;
import com.modernchat.util.ChatUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.Notification;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.TrayIcon;
import java.awt.Window;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class NotificationService
{
    @Inject private Client client;
    @Inject private Notifier notifier;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private ModernChatConfig config;

    public void pushChatMessage(String text) {
        pushChatMessage(text, ChatMessageType.GAMEMESSAGE);
    }

    public void pushChatMessage(ChatMessageBuilder builder) {
        pushChatMessage(builder, ChatMessageType.GAMEMESSAGE);
    }

    public void pushChatMessage(ChatMessageBuilder builder, ChatMessageType type) {
        String msg = new ChatMessageBuilder()
            .append(Color.CYAN, prepareMessage(""))
            .append(builder.build(), false)
            .build();

        chatMessageManager.queue(QueuedMessage.builder()
            .type(type)
            .runeLiteFormattedMessage(msg)
            .build());
    }

    public void pushChatMessage(String text, ChatMessageType type) {
        String msg = new ChatMessageBuilder()
            .append(Color.CYAN, prepareMessage(""))
            .append(text)
            .build();

        chatMessageManager.queue(QueuedMessage.builder()
            .type(type)
            .runeLiteFormattedMessage(msg)
            .build());
    }

    public void showInfoMessageBox(String title, String message) {
        showInfoMessageBox(title, message, null);
    }

    public void showInfoMessageBox(String title, String message, Icon icon) {
        showMessageBox(title, message, JOptionPane.INFORMATION_MESSAGE, icon);
    }

    public void showErrorMessageBox(String title, String message) {
        showErrorMessageBox(title, message, null);
    }

    public void showErrorMessageBox(String title, String message, Icon icon) {
        showMessageBox(title, message, JOptionPane.ERROR_MESSAGE, icon);
    }

    public void showWarningMessageBox(String title, String message) {
        showWarningMessageBox(title, message, null);
    }

    public void showWarningMessageBox(String title, String message, Icon icon) {
        showMessageBox(title, message, JOptionPane.WARNING_MESSAGE, icon);
    }

    private void showQuestionMessageBox(String title, String message) {
        showQuestionMessageBox(title, message, null);
    }

    public void showQuestionMessageBox(String title, String message, Icon icon) {
        showMessageBox(title, message, JOptionPane.QUESTION_MESSAGE, icon);
    }

    @SuppressWarnings("MagicConstant")
    private void showMessageBox(String title, String message, int messageType, Icon icon) {
        Window parent = SwingUtilities.getWindowAncestor(client.getCanvas());
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(
                parent,
                message,
                prepareMessage(title),
                messageType,
                icon
            )
        );
    }

    public void showInfoConfirmDialog(String title, String message,
                                      Consumer<Integer> callback) {
        showInfoConfirmDialog(title, message, null, callback);
    }

    public void showInfoConfirmDialog(String title, String message, Icon icon,
                                      Consumer<Integer> callback) {
        showConfirmDialog(title, message, JOptionPane.INFORMATION_MESSAGE, icon, callback);
    }

    public void showErrorConfirmDialog(String title, String message,
                                       Consumer<Integer> callback) {
        showErrorConfirmDialog(title, message, null, callback);
    }

    public void showErrorConfirmDialog(String title, String message, Icon icon,
                                       Consumer<Integer> callback) {
        showConfirmDialog(title, message, JOptionPane.ERROR_MESSAGE, icon, callback);
    }

    public void showWarningConfirmDialog(String title, String message,
                                         Consumer<Integer> callback) {
        showWarningConfirmDialog(title, message, null, callback);
    }

    public void showWarningConfirmDialog(String title, String message, Icon icon,
                                         Consumer<Integer> callback) {
        showConfirmDialog(title, message, JOptionPane.WARNING_MESSAGE, icon, callback);
    }

    public void showQuestionConfirmDialog(String title, String message,
                                           Consumer<Integer> callback) {
        showQuestionConfirmDialog(title, message, null, callback);
    }

    public void showQuestionConfirmDialog(String title, String message, Icon icon,
                                          Consumer<Integer> callback) {
        showConfirmDialog(title, message, JOptionPane.QUESTION_MESSAGE, icon, callback);
    }

    @SuppressWarnings("MagicConstant")
    private void showConfirmDialog(String title, String message, int messageType, Icon icon,
                                   Consumer<Integer> callback) {
        Window parent = SwingUtilities.getWindowAncestor(client.getCanvas());
        SwingUtilities.invokeLater(() -> {
            int result = JOptionPane.showConfirmDialog(
                parent,
                message,
                prepareMessage(title),
                JOptionPane.YES_NO_OPTION,
                messageType,
                icon
            );

            if (callback != null) {
                 callback.accept(result);
            }
        });
    }

    public void notify(String message) {
        notifier.notify(prepareMessage(message));
    }

    public void notify(String message, TrayIcon.MessageType messageType) {
        notifier.notify(prepareMessage(message), messageType);
    }

    public void notify(Notification notification, String message) {
        notifier.notify(notification, prepareMessage(message));
    }

    public static String prepareMessage(String message) {
        return prepareMessage(message, "{} ");
    }

    public static String prepareMessage(String message, String tagFormat) {
        return tagFormat.replace("{}", ChatUtil.MODERN_CHAT_TAG) + message;
    }

    public void pushHelperNotification(String message) {
        pushHelperNotification(new ChatMessageBuilder().append(message));
    }

    public void pushHelperNotification(ChatMessageBuilder builder) {
        if (!config.general_HelperNotifications())
            return;

        pushChatMessage(builder);
    }
}
