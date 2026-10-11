package dev.sentient.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.sentient.llm.ChatMessage;
import dev.sentient.net.ChatRequestPacket;
import dev.sentient.net.SentientNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NpcChatScreen extends Screen {

    private enum Role {
        USER, NPC, ERROR
    }

    private record Line(Role role, String text) {
    }

    private record WrappedLine(FormattedCharSequence seq, int color) {
    }

    private final int entityId;
    private final String npcName;
    private final List<Line> lines = new ArrayList<>();
    private EditBox input;
    private int scroll;

    public NpcChatScreen(int entityId, String npcName, List<ChatMessage> history) {
        super(Component.literal("Talking to " + npcName));
        this.entityId = entityId;
        this.npcName = npcName;
        for (ChatMessage m : history) {
            if (ChatMessage.USER.equals(m.role)) {
                lines.add(new Line(Role.USER, stripSpeaker(m.content)));
            } else if (ChatMessage.ASSISTANT.equals(m.role)) {
                lines.add(new Line(Role.NPC, m.content));
            }
        }
    }

    public int entityId() {
        return entityId;
    }

    @Override
    protected void init() {
        int w = Math.min(320, this.width - 40);
        this.input = new EditBox(this.font, this.width / 2 - w / 2, this.height - 30, w, 18,
                Component.literal("Say something..."));
        this.input.setMaxLength(512);
        this.addRenderableWidget(this.input);
        this.setFocused(this.input);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int panelTop = 34;
        int panelBottom = this.height - 46;
        int lineHeight = 10;
        int wrapWidth = this.width - 70;

        List<WrappedLine> wrapped = new ArrayList<>();
        for (Line l : lines) {
            int color = switch (l.role()) {
                case USER -> 0x55FFFF;
                case NPC -> 0xFFFFFF;
                case ERROR -> 0xFF5555;
            };
            for (FormattedCharSequence seq : this.font.split(Component.literal(l.text()), wrapWidth)) {
                wrapped.add(new WrappedLine(seq, color));
            }
        }

        int visible = Math.max(1, (panelBottom - panelTop) / lineHeight);
        int maxScroll = Math.max(0, wrapped.size() - visible);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (scroll < 0) {
            scroll = 0;
        }
        int end = wrapped.size() - scroll;
        int start = Math.max(0, end - visible);
        int y = panelTop;
        for (int i = start; i < end; i++) {
            WrappedLine wl = wrapped.get(i);
            g.drawString(this.font, wl.seq(), 35, y, wl.color(), false);
            y += lineHeight;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll += delta > 0 ? 1 : -1;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        if (this.input != null && this.input.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send() {
        if (this.input == null) {
            return;
        }
        String text = this.input.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        lines.add(new Line(Role.USER, text));
        scroll = 0;
        SentientNetwork.CHANNEL.sendToServer(new ChatRequestPacket(entityId, text));
        this.input.setValue("");
    }

    public void appendNpc(String text) {
        lines.add(new Line(Role.NPC, text));
        scroll = 0;
    }

    public void appendError(String text) {
        lines.add(new Line(Role.ERROR, text));
        scroll = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientPacketHandler.screenClosed(this);
        super.onClose();
    }

    private static String stripSpeaker(String content) {
        int idx = content.indexOf(" says: ");
        if (idx >= 0) {
            return content.substring(idx + " says: ".length());
        }
        return content;
    }
}
