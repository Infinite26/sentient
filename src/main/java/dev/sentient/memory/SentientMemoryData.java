package dev.sentient.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-scoped store of every NPC's memory. */
public class SentientMemoryData extends SavedData {

    public static final String DATA_NAME = "sentient_memory";

    private final Map<UUID, NpcMemory> memories = new HashMap<>();

    public static SentientMemoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SentientMemoryData::load, SentientMemoryData::new, DATA_NAME);
    }

    public NpcMemory get(UUID id) {
        return memories.get(id);
    }

    public NpcMemory getOrCreate(UUID id, String name, String typeId) {
        NpcMemory mem = memories.get(id);
        if (mem == null) {
            mem = new NpcMemory(id, name, typeId);
            memories.put(id, mem);
            setDirty();
        } else {
            if (name != null && !name.isBlank() && !name.equals(mem.name())) {
                mem.setName(name);
                setDirty();
            }
            if (typeId != null && !typeId.equals(mem.typeId())) {
                mem.setTypeId(typeId);
                setDirty();
            }
        }
        return mem;
    }

    public boolean forget(UUID id) {
        boolean removed = memories.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int size() {
        return memories.size();
    }

    public void clearAll() {
        memories.clear();
        setDirty();
    }

    public static SentientMemoryData load(CompoundTag tag) {
        SentientMemoryData data = new SentientMemoryData();
        ListTag list = tag.getList("memories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            NpcMemory mem = NpcMemory.load(entry);
            data.memories.put(mem.id(), mem);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (NpcMemory mem : memories.values()) {
            list.add(mem.save());
        }
        tag.put("memories", list);
        return tag;
    }
}
