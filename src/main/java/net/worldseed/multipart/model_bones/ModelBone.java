package net.worldseed.multipart.model_bones;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.worldseed.multipart.Quaternion;
import net.worldseed.multipart.animations.ModelAnimation;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@ApiStatus.Internal
public interface ModelBone {
    CompletableFuture<Void> spawn(Instance instance, Point position);
    Point applyTransform(Point p);
    void draw();
    void destroy();

    Point simulateTransform(Point p, String animation, int time);
    Point simulateRotation(String animation, int time);

    void setState(String state);
    void setParent(ModelBone parent);

    String getName();
    Entity getEntity();
    Point getOffset();
    ModelBone getParent();
    Point getPropogatedRotation();

    Point calculateRotation(Point p, Point rotation, Point pivot);
    Quaternion calculateFinalAngle(Quaternion q);
    Pos calculatePosition();
    Point calculateRotation();

    void addChild(ModelBone child);
    void addAnimation(ModelAnimation animation);
}