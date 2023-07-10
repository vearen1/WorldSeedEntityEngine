package net.worldseed.multipart.events;

import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.sound.SoundEvent;
import net.worldseed.multipart.GenericModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModelDamageEvent implements ModelEvent, CancellableEvent {
    private final GenericModel model;
    private final DamageType damageType;
    private float damage;
    private SoundEvent sound;

    private boolean animation = true;

    private boolean cancelled;

    public ModelDamageEvent(GenericModel model, @NotNull DamageType damageType,
                            float damage, @Nullable SoundEvent sound) {
        this.model = model;
        this.damageType = damageType;
        this.damage = damage;
        this.sound = sound;
    }

    public ModelDamageEvent(GenericModel model, EntityDamageEvent event) {
        this(model, event.getDamageType(), event.getDamage(), event.getSound());
        this.animation = event.shouldAnimate();
    }

    /**
     * Gets the damage type.
     *
     * @return the damage type
     */
    @NotNull
    public DamageType getDamageType() {
        return damageType;
    }

    /**
     * Gets the damage amount.
     *
     * @return the damage amount
     */
    public float getDamage() {
        return damage;
    }

    /**
     * Changes the damage amount.
     *
     * @param damage the new damage amount
     */
    public void setDamage(float damage) {
        this.damage = damage;
    }

    /**
     * Gets the damage sound.
     *
     * @return the damage sound
     */
    @Nullable
    public SoundEvent getSound() {
        return sound;
    }

    /**
     * Changes the damage sound.
     *
     * @param sound the new damage sound
     */
    public void setSound(@Nullable SoundEvent sound) {
        this.sound = sound;
    }

    /**
     * Gets whether the damage animation should be played.
     *
     * @return true if the animation should be played
     */
    public boolean shouldAnimate() {
        return animation;
    }

    /**
     * Sets whether the damage animation should be played.
     *
     * @param animation whether the animation should be played or not
     */
    public void setAnimation(boolean animation) {
        this.animation = animation;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public GenericModel getModel() {
        return model;
    }
}
