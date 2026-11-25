package traben.entity_model_features.utils;

import net.minecraft.Entity;
import net.minecraft.TileEntity;
import net.minecraft.Vec3;
import traben.entity_model_features.EMFManager;
import traben.entity_texture_features.features.ETFTileEntity;
import traben.entity_texture_features.utils.ETFEntity;
import java.util.Map;

public interface EMFEntity extends ETFEntity {

    static EMFEntity of(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof EMFEntity bridge) return bridge;
        return new EMFEntity() { public Entity etf$asEntity() { return entity; } };
    }
    static EMFEntity of(TileEntity tile) {
        if (tile == null) return null;
        if (tile instanceof EMFEntity bridge) return bridge;
        return of(ETFTileEntity.of(tile));
    }
    default double emf$prevX() { return etf$asEntity().prevPosX; }
    default double emf$getX() { return etf$asEntity().posX; }
    default double emf$prevY() { return etf$asEntity().prevPosY; }
    default double emf$getY() { return etf$asEntity().posY; }
    default double emf$prevZ() { return etf$asEntity().prevPosZ; }
    default double emf$getZ() { return etf$asEntity().posZ; }
    default float emf$prevPitch() { return etf$asEntity().prevRotationPitch; }
    default float emf$getPitch() { return etf$asEntity().rotationPitch; }
    default boolean emf$isTouchingWater() { return etf$asEntity().isInWater(); }
    default boolean emf$isOnFire() { return etf$asEntity().isBurning(); }
    default boolean emf$hasVehicle() { return etf$asEntity().ridingEntity != null; }
    default boolean emf$isOnGround() { return etf$asEntity().onGround; }
    default boolean emf$isAlive() { return etf$isBlockEntity() || etf$asEntity().isEntityAlive(); }
    default boolean emf$isGlowing() { return false; }
    default boolean emf$isInLava() { return etf$asEntity().handleLavaMovement(); }
    default boolean emf$isInvisible() { return etf$asEntity().isInvisible(); }
    default boolean emf$hasPassengers() { return etf$asEntity().riddenByEntity != null; }
    default boolean emf$isSneaking() { return etf$asEntity().isSneaking(); }
    default boolean emf$isSprinting() { return etf$asEntity().isSprinting(); }
    default boolean emf$isWet() { return etf$asEntity().isWet(); }
    default float emf$age() { return etf$asEntity().ticksExisted; }
    default float emf$getYaw() { return etf$asEntity().rotationYaw; }
    default Vec3 emf$getVelocity() { return etf$getVelocity(); }
    default String emf$getTypeString() { return etf$getEntityKey(); }
    default Map<String, Float> emf$getVariableMap() { return EMFManager.INSTANCE.getFloatVariables(etf$asEntity()); }
}
