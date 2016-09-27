package com.fundynamic.d2tm.game.entities.structures;

import com.fundynamic.d2tm.Game;
import com.fundynamic.d2tm.game.behaviors.*;
import com.fundynamic.d2tm.game.entities.*;
import com.fundynamic.d2tm.game.entities.entitiesdata.EntitiesData;
import com.fundynamic.d2tm.game.entities.entitybuilders.EntityBuilderType;
import com.fundynamic.d2tm.game.entities.entitybuilders.SingleEntityBuilder;
import com.fundynamic.d2tm.game.entities.entitybuilders.AbstractBuildableEntity;
import com.fundynamic.d2tm.game.map.Cell;
import com.fundynamic.d2tm.game.rendering.gui.battlefield.RenderQueue;
import com.fundynamic.d2tm.math.Coordinate;
import com.fundynamic.d2tm.math.MapCoordinate;
import com.fundynamic.d2tm.utils.Colors;
import com.fundynamic.d2tm.utils.SlickUtils;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheet;

import java.util.ArrayList;
import java.util.List;

public class Structure extends Entity implements Selectable, Destructible, Focusable, EntityBuilder {

    private EntityBuilder entityBuilder;

    // Behaviors
    private final FadingSelection fadingSelection;
    protected final HitPointBasedDestructibility hitPointBasedDestructibility;

    // Implementation
    private float animationTimer;
    private static final int ANIMATION_FRAME_COUNT = 2;
    private static final int ANIMATION_FRAMES_PER_SECOND = 5;

    private boolean hasSpawnedExplosions;

    public Structure(Coordinate coordinate, SpriteSheet spritesheet, Player player, EntityData entityData, EntityRepository entityRepository) {
        super(coordinate,
                spritesheet,
                entityData,
                player,
                entityRepository
        );

        this.fadingSelection = new FadingSelection(entityData.getWidth(), entityData.getHeight());
        this.hitPointBasedDestructibility = new HitPointBasedDestructibility(entityData.hitPoints, entityData.getWidth());

        List<EntityData> entityDatas = new ArrayList<>();
        if (entityData.entityBuilderType == EntityBuilderType.STRUCTURES) {
            for (String buildableEntity : entityData.getEntityDataKeysToBuild()) {
                entityDatas.add(entityRepository.getEntityData(EntityType.STRUCTURE, buildableEntity));
            }
        }

        if (entityData.entityBuilderType == EntityBuilderType.UNITS) {
            for (String buildableEntity : entityData.getEntityDataKeysToBuild()) {
                entityDatas.add(entityRepository.getEntityData(EntityType.UNIT, buildableEntity));
            }
        }

        this.entityBuilder = new SingleEntityBuilder(entityDatas);

    }

    public Image getSprite() {
        int animationFrame = (int)animationTimer;
        return spritesheet.getSprite(0, animationFrame);
    }

    public void update(float deltaInSeconds) {
        if (this.isDestroyed()) {
            System.out.println("I (" + this.toString() + ") am dead, so I won't update anymore.");
            return;
        }

        // REVIEW: maybe base the animation on a global timer, so all animations are in-sync?
        float offset = deltaInSeconds * ANIMATION_FRAMES_PER_SECOND;
        animationTimer = (animationTimer + offset) % ANIMATION_FRAME_COUNT;

        this.fadingSelection.update(deltaInSeconds);

        if (hitPointBasedDestructibility.hasDied()) {
            hasSpawnedExplosions = true;
            for (Coordinate centeredPos : entityData.getAllCellsAsCenteredCoordinates(coordinate)) {
                entityRepository.explodeAt(centeredPos, entityData, player);
            }
        }

        if (hasBuildingEntity()) {
            this.entityBuilder.update(deltaInSeconds);
        }

        if (isAwaitingSpawning()) {
            List<MapCoordinate> allSurroundingCellsAsCoordinates = getAllSurroundingCellsAsCoordinates();
            for (MapCoordinate potentiallySpawnableCoordinate : allSurroundingCellsAsCoordinates) {
                AbstractBuildableEntity buildingEntity = entityBuilder.getBuildingEntity();
                if (this.entityRepository.isPassable(this, potentiallySpawnableCoordinate)) {
                    Coordinate absoluteCoordinate = potentiallySpawnableCoordinate.toCoordinate();

                    Entity entity = this.entityRepository.placeOnMap(
                            absoluteCoordinate,
                            buildingEntity.getEntityData(),
                            this.player
                    );

                    this.entityIsDelivered(entity);
                    break;
                }
            }
            this.entityIsDelivered(this);
        }
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.STRUCTURE;
    }

    @Override
    public void render(Graphics graphics, int x, int y) {
        Image sprite = getSprite();
        graphics.drawImage(sprite, x, y);
        MapCoordinate mapCoordinate = coordinate.toMapCoordinate();
        if (Game.DEBUG_INFO) {
            SlickUtils.drawShadowedText(
                    graphics,
                    Colors.WHITE,
                    "" + mapCoordinate.getXAsInt() + "," + mapCoordinate.getYAsInt(),
                    x,
                    y);
        }
    }

    public void select() {
        fadingSelection.select();
    }

    public void deselect() {
        fadingSelection.deselect();
    }

    @Override
    public boolean isSelected() {
        return fadingSelection.isSelected();
    }

    @Override
    public void getsFocus() {
        fadingSelection.getsFocus();
    }

    @Override
    public void lostFocus() {
        fadingSelection.lostFocus();
    }

    @Override
    public String toString() {
        return "Structure{" +
                "fadingSelection=" + fadingSelection +
                ", animationTimer=" + animationTimer +
                ", hitPoints=" + hitPointBasedDestructibility +
                '}';
    }

    @Override
    public void takeDamage(int hitPoints, Entity origin) {
        hitPointBasedDestructibility.takeDamage(hitPoints);
        System.out.println("I took damage " + hitPointBasedDestructibility);
    }

    @Override
    public boolean isDestroyed() {
        return hasSpawnedExplosions && hitPointBasedDestructibility.hasDied();
    }

    @Override
    public int getHitPoints() {
        return hitPointBasedDestructibility.getHitPoints();
    }

    @Override
    public void enrichRenderQueue(RenderQueue renderQueue) {
        if (isSelected()) {
            renderQueue.putEntityGui(this.hitPointBasedDestructibility, this.getCoordinate());
            renderQueue.putEntityGui(this.fadingSelection, this.getCoordinate());
        } else {
            if (fadingSelection.hasFocus()) {
                renderQueue.putEntityGui(this.hitPointBasedDestructibility, this.getCoordinate());
            }
        }
    }

    public boolean hasFocus() {
        return fadingSelection.hasFocus();
    }

    @Override
    public List<AbstractBuildableEntity> getBuildList() {
        return this.entityBuilder.getBuildList();
    }

    @Override
    public boolean hasBuildingEntity() {
        return this.entityBuilder.hasBuildingEntity();
    }

    @Override
    public AbstractBuildableEntity getBuildingEntity() {
        return this.entityBuilder.getBuildingEntity();
    }

    @Override
    public void buildEntity(AbstractBuildableEntity placementBuildableEntity) {
        this.entityBuilder.buildEntity(placementBuildableEntity);
    }

    @Override
    public boolean isAwaitingPlacement() {
        return this.entityBuilder.isAwaitingPlacement();
    }

    @Override
    public boolean isAwaitingSpawning() {
        return this.entityBuilder.isAwaitingSpawning();
    }

    @Override
    public void entityIsDelivered(Entity entity) {
        this.entityBuilder.entityIsDelivered(entity);
    }

    @Override
    public boolean hasBuildingEntity(AbstractBuildableEntity placementBuildableEntity) {
        return this.entityBuilder.hasBuildingEntity(placementBuildableEntity);
    }
}
