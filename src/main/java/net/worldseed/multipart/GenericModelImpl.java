package net.worldseed.multipart;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.audience.Audience;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerProcess;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.SendablePacket;
import net.worldseed.gestures.EmoteModel;
import net.worldseed.multipart.animations.AnimationHandlerImpl;
import net.worldseed.multipart.events.AnimationCompleteEvent;
import net.worldseed.multipart.events.ModelEvent;
import net.worldseed.multipart.model_bones.*;
import net.worldseed.multipart.model_bones.armour_stand.ModelBonePartArmourStandHand;
import net.worldseed.multipart.model_bones.display_entity.ModelBoneHeadDisplay;
import net.worldseed.multipart.model_bones.display_entity.ModelBonePartDisplay;
import net.worldseed.multipart.model_bones.misc.ModelBoneHitbox;
import net.worldseed.multipart.model_bones.misc.ModelBoneNametag;
import net.worldseed.multipart.model_bones.misc.ModelBoneSeat;
import net.worldseed.multipart.model_bones.misc.ModelBoneVFX;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class GenericModelImpl implements GenericModel {
    protected final LinkedHashMap<String, ModelBone> parts = new LinkedHashMap<>();
    protected final Set<ModelBoneImpl> viewableBones = new LinkedHashSet<>();
    protected final Set<ModelBoneHitbox> hittableBones = new LinkedHashSet<>();
    protected final Map<String, ModelBoneVFX> VFXBones = new LinkedHashMap<>();

    private ModelBoneSeat seat;
    private ModelBoneHead head;
    private ModelBoneNametag nametag;

    private Pos position;
    private double globalRotation;
    protected Instance instance;

    private final Set<Player> viewers = new HashSet<>();
    private final EventNode<ModelEvent> eventNode;

    public GenericModelImpl() {
        final ServerProcess process = MinecraftServer.process();
        if (process != null) {
            this.eventNode = process.eventHandler().map(this, EventFilter.from(ModelEvent.class, GenericModel.class, ModelEvent::getModel));
        } else {
            // Local nodes require a server process
            this.eventNode = null;
        }
    }

    @Override
    public @NotNull EventNode<ModelEvent> eventNode() {
        return eventNode;
    }

    @Override
    public double getGlobalRotation() {
        return globalRotation;
    }

    @Override
    public Pos getPosition() {
        return position;
    }

    public void triggerAnimationEnd(String animation, AnimationHandlerImpl.AnimationDirection direction) {
        MinecraftServer.getGlobalEventHandler().call(new AnimationCompleteEvent(this, animation, direction));
    }

    public void init(@Nullable Instance instance, @NotNull Pos position) {
        init(instance, position, 1);
    }

    public void init(@Nullable Instance instance, @NotNull Pos position, float scale) {
        this.instance = instance;
        this.position = position;

        JsonObject loadedModel = ModelLoader.loadModel(getId());
        this.setGlobalRotation(position.yaw());

        loadBones(loadedModel, scale);

        for (ModelBone modelBonePart : this.parts.values()) {
            if (modelBonePart instanceof ModelBoneViewable)
                viewableBones.add((ModelBoneImpl) modelBonePart);
            else if (modelBonePart instanceof ModelBoneHitbox hitbox)
                hittableBones.add(hitbox);
            else if (modelBonePart instanceof ModelBoneVFX vfx)
                VFXBones.put(vfx.getName(), vfx);

            modelBonePart.spawn(instance, modelBonePart.calculatePosition()).join();
        }

        draw();
        draw();
        draw();

        this.setState("normal");
    }

    @Override
    public void setScale(float scale) {
        for (ModelBone modelBonePart : this.parts.values()) {
            modelBonePart.setScale(scale);
        }
    }

    protected void loadBones(JsonObject loadedModel, float scale) {
        // Build bones
        for (JsonElement bone : loadedModel.get("minecraft:geometry").getAsJsonArray().get(0).getAsJsonObject().get("bones").getAsJsonArray()) {
            JsonElement pivot = bone.getAsJsonObject().get("pivot");
            String name = bone.getAsJsonObject().get("name").getAsString();

            Point boneRotation = ModelEngine.getPos(bone.getAsJsonObject().get("rotation")).orElse(Pos.ZERO).mul(-1, -1, 1);
            Point pivotPos = ModelEngine.getPos(pivot).orElse(Pos.ZERO).mul(-1,1,1);

            ModelBone modelBonePart = null;

            if (name.equals("nametag")) {
                this.nametag = new ModelBoneNametag(pivotPos, name, boneRotation, this, null, scale);
                modelBonePart = nametag;
            } else if (name.contains("hitbox")) {
                addHitboxParts(pivotPos, name, boneRotation, this, bone.getAsJsonObject().getAsJsonArray("cubes"), this.parts, scale);
            } else if (name.contains("vfx")) {
                modelBonePart = new ModelBoneVFX(pivotPos, name, boneRotation, this, scale);
            } else if (name.contains("seat")) {
                modelBonePart = new ModelBoneSeat(pivotPos, name, boneRotation, this, scale);
                this.seat = (ModelBoneSeat) modelBonePart;
            } else if (name.equals("head")) {
                this.head = new ModelBoneHeadDisplay(pivotPos, name, boneRotation, this, scale);
                modelBonePart = (ModelBone) head;
            } else {
                if (this instanceof EmoteModel) {
                    modelBonePart = new ModelBonePartArmourStandHand(pivotPos, name, boneRotation, this, scale);
                } else {
                    modelBonePart = new ModelBonePartDisplay(pivotPos, name, boneRotation, this, scale);
                }
            }

            if (modelBonePart != null) this.parts.put(name, modelBonePart);
        }

        // Link parents
        for (JsonElement bone : loadedModel.get("minecraft:geometry").getAsJsonArray().get(0).getAsJsonObject().get("bones").getAsJsonArray()) {
            String name = bone.getAsJsonObject().get("name").getAsString();
            JsonElement parent = bone.getAsJsonObject().get("parent");
            String parentString = parent == null ? null : parent.getAsString();

            if (parentString != null) {
                ModelBone child = this.parts.get(name);

                if (child == null) continue;
                ModelBone parentBone = this.parts.get(parentString);
                child.setParent(parentBone);
                parentBone.addChild(child);
            }
        }
    }

    private void addHitboxParts(Point pivotPos, String name, Point boneRotation, GenericModelImpl genericModel, JsonArray cubes, LinkedHashMap<String, ModelBone> parts, float scale) {
        if (cubes.size() < 1) return;

        var cube = cubes.get(0);
        JsonArray sizeArray = cube.getAsJsonObject().get("size").getAsJsonArray();
        JsonArray p = cube.getAsJsonObject().get("pivot").getAsJsonArray();

        Point sizePoint = new Vec(sizeArray.get(0).getAsFloat(), sizeArray.get(1).getAsFloat(), sizeArray.get(2).getAsFloat());
        Point pivotPoint = new Vec(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());

        var newOffset = pivotPoint.mul(-1, 1, 1);

        ModelBone created = new ModelBoneHitbox(pivotPos, name, boneRotation, genericModel, newOffset, sizePoint.x(), sizePoint.y(), cubes, true, scale);
        parts.put(name, created);
    }

    public void setNametagEntity(BoneEntity entity) {
        if (this.nametag != null) this.nametag.linkEntity(entity);
    }

    public Entity getNametagEntity() {
        if (this.nametag != null) return this.nametag.getStand();
        return null;
    }

    public void setPosition(Pos pos) {
        this.position = pos;
    }

    public void setGlobalRotation(double rotation) {
        this.globalRotation = rotation;
    }

    public Instance getInstance() {
        return instance;
    }

    public void mountEntity(Entity entity) {
        if (this.seat != null) {
            this.seat.getEntity().addPassenger(entity);
        }
    }

    public void dismountEntity(Entity e) {
        if (this.seat != null)
            this.seat.getEntity().removePassenger(e);
    }

    public Set<Entity> getPassengers() {
        if (this.seat == null || this.seat.getEntity() == null) return Set.of();
        return this.seat.getEntity().getPassengers();
    }

    public void setState(String state) {
        for (ModelBoneImpl part : viewableBones) {
            part.setState(state);
        }
    }

    public ModelBone getPart(String boneName) {
        return this.parts.get(boneName);
    }

    public ModelBone getSeat() {
        return this.seat;
    }

    public void draw() {
        for (ModelBone modelBonePart : this.parts.values()) {
            if (modelBonePart.getParent() == null)
                modelBonePart.draw();
        }
    }

    public void destroy() {
        for (ModelBone modelBonePart : this.parts.values()) {
            modelBonePart.destroy();
        }

        this.viewableBones.clear();
        this.hittableBones.clear();
        this.VFXBones.clear();
        this.parts.clear();
    }

    public void removeHitboxes() {
        hittableBones.forEach(ModelBoneImpl::destroy);
        hittableBones.clear();
    }

    @Override
    public Point getVFX(String name) {
        ModelBoneVFX found = VFXBones.get(name);
        if (found == null) return null;
        return found.getPosition();
    }

    @Override
    public void setHeadRotation(double rotation) {
        if (this.head != null) this.head.setRotation(rotation);
    }

    public List<ModelBone> getParts() {
        return this.parts.values().stream().filter(Objects::nonNull).toList();
    }

    @Override
    public Point getBoneAtTime(String animation, String boneName, int time) {
        var bone = this.parts.get(boneName);

        Point p = bone.getOffset();
        p = bone.simulateTransform(p, animation, time);
        p = bone.calculateRotation(p, new Vec(0, 180 - getGlobalRotation(), 0), getPivot());

        return p.div(6.4, 6.4, 6.4)
                .add(getPosition())
                .add(getGlobalOffset());
    }

    public Pos getPivot() {
        return Pos.ZERO;
    }
    public Pos getGlobalOffset() {
        return Pos.ZERO;
    }

    public Point getDiff(String boneName) {
        return ModelEngine.diffMappings.get(getId() + "/" + boneName);
    }

    public Point getOffset(String boneName) {
        return ModelEngine.offsetMappings.get(getId() + "/" + boneName);
    }

    @Override
    public boolean isViewer(@NotNull Player player) {
        return this.viewers.contains(player);
    }

    @Override
    public void sendPacketToViewers(@NotNull SendablePacket packet) {
        for (Player viewer : this.viewers) {
            viewer.sendPacket(packet);
        }
    }

    @Override
    public void sendPacketsToViewers(@NotNull Collection<SendablePacket> packets) {
        for (Player viewer : this.viewers) {
            for (SendablePacket packet : packets) {
                viewer.sendPacket(packet);
            }
        }
    }

    @Override
    public void sendPacketsToViewers(@NotNull SendablePacket... packets) {
        for (Player viewer : this.viewers) {
            for (SendablePacket packet : packets) {
                viewer.sendPacket(packet);
            }
        }
    }

    @Override
    public void sendPacketToViewersAndSelf(@NotNull SendablePacket packet) {
        sendPacketToViewers(packet);
    }

    @Override
    public @NotNull Audience getViewersAsAudience() {
        return Audience.audience(viewers);
    }

    @Override
    public @NotNull Iterable<? extends Audience> getViewersAsAudiences() {
        return List.of(getViewersAsAudience());
    }

    @Override
    public boolean addViewer(@NotNull Player player) {
        getParts().forEach(part -> part.addViewer(player));
        return this.viewers.add(player);
    }

    @Override
    public boolean removeViewer(@NotNull Player player) {
        getParts().forEach(part -> part.removeViewer(player));
        return this.viewers.remove(player);
    }

    @Override
    public @NotNull Set<@NotNull Player> getViewers() {
        return Set.copyOf(this.viewers);
    }
}