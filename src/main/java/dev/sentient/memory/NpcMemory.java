package dev.sentient.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import dev.sentient.llm.ChatMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Persistent conversation memory and identity for one NPC. */
public class NpcMemory {

    private final UUID id;
    private String name;
    private String typeId;
    private final Deque<ChatMessage> messages = new ArrayDeque<>();

    public NpcMemory(UUID id, String name, String typeId) {
        this.id = id;
        this.name = name;
        this.typeId = typeId;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String typeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public void add(ChatMessage message, int maxMessages) {
        messages.addLast(message);
        while (messages.size() > Math.max(2, maxMessages)) {
            messages.removeFirst();
        }
    }

    public void clear() {
        messages.clear();
    }

    public List<ChatMessage> last(int n) {
        List<ChatMessage> all = new ArrayList<>(messages);
        if (n <= 0 || all.size() <= n) {
            return all;
        }
        return all.subList(all.size() - n, all.size());
    }

    public List<ChatMessage> all() {
        return new ArrayList<>(messages);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name == null ? "" : name);
        t.putString("type", typeId == null ? "" : typeId);
        ListTag list = new ListTag();
        for (ChatMessage m : messages) {
            CompoundTag mt = new CompoundTag();
            mt.putString("role", m.role);
            mt.putString("content", m.content);
            list.add(mt);
        }
        t.put("messages", list);
        return t;
    }

    public static NpcMemory load(CompoundTag t) {
        UUID id = t.hasUUID("id") ? t.getUUID("id") : UUID.randomUUID();
        NpcMemory mem = new NpcMemory(id, t.getString("name"), t.getString("type"));
        ListTag list = t.getList("messages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag mt = list.getCompound(i);
            mem.messages.addLast(new ChatMessage(mt.getString("role"), mt.getString("content")));
        }
        return mem;
    }
}
