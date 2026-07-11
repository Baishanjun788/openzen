package shit.zen.modules.impl.player;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Objects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.settings.impl.StringSetting;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.game.RotationUtil;
import shit.zen.utils.misc.ChatUtil;

public class FollowPlayer extends Module {
    public static FollowPlayer INSTANCE;

    public final StringSetting targetName = new StringSetting("Target", "");
    public final NumberSetting followDistance = new NumberSetting("Follow Distance", 3.0, 1.0, 10.0, 0.1);
    public final BooleanSetting followView = new BooleanSetting("Follow View", true);
    public final BooleanSetting followPath = new BooleanSetting("Follow Path", false);
    public final BooleanSetting autoJump = new BooleanSetting("Auto Jump", true);
    public final BooleanSetting autoClimb = new BooleanSetting("Auto Climb", true);
    public final BooleanSetting openDoors = new BooleanSetting("Open Doors", true);
    public final BooleanSetting pressButtons = new BooleanSetting("Press Buttons", true);

    private int stuckTicks;
    private int jumpTicks;
    private float lastYaw;
    private float lastPitch;
    private boolean lastTickForcedRotation;
    private boolean manualViewControl;
    private int manualViewControlTicks;
    private boolean followShouldMove;
    private float detourDirection;
    private Deque<FollowPoint> followPathPoints = new ArrayDeque<>();
    private Vec3 currentFollowPos;

    private static class FollowPoint {
        private final Vec3 pos;
        private final float yaw;
        private final float pitch;

        private FollowPoint(Vec3 pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public FollowPlayer() {
        super("FollowPlayer", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        this.stuckTicks = 0;
        this.jumpTicks = 0;
        this.detourDirection = 0;
        this.followPathPoints.clear();
        this.lastYaw = 0.0f;
        this.lastPitch = 0.0f;
        this.lastTickForcedRotation = false;
        this.manualViewControl = false;
        this.manualViewControlTicks = 0;
        if (mc.player != null) {
            this.lastYaw = mc.player.getYRot();
            this.lastPitch = mc.player.getXRot();
        }
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.stuckTicks = 0;
        this.jumpTicks = 0;
        this.followPathPoints.clear();
        this.manualViewControl = false;
        this.manualViewControlTicks = 0;
        this.lastTickForcedRotation = false;
        this.resetFollowControls();
        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (this.targetName.getValue() == null || this.targetName.getValue().isBlank()) {
            return;
        }

        Player target = this.findTarget();
        if (target == null) {
            return;
        }

        Vec3 targetPos = target.position();
        Vec3 playerPos = mc.player.position();
        Vec3 followPos = targetPos;
        FollowPoint currentPoint = null;
        if (this.followPath.getValue()) {
            this.updateFollowPath(targetPos, target.getYRot(), target.getXRot());
            currentPoint = this.followPathPoints.peekFirst();
            if (currentPoint != null) {
                followPos = currentPoint.pos;
                this.currentFollowPos = currentPoint.pos;
            } else {
                this.currentFollowPos = targetPos;
            }
        } else {
            this.followPathPoints.clear();
            this.currentFollowPos = targetPos;
        }
        double distance = playerPos.distanceTo(followPos);
        boolean obstacle = this.isObstacleAhead(followPos, playerPos);

        this.updateManualViewControl();
        if (this.followView.getValue() && !this.manualViewControl && !obstacle) {
            if (this.followPath.getValue() && currentPoint != null) {
                this.faceRotation(currentPoint.yaw, currentPoint.pitch);
            } else {
                this.facePosition(followPos, playerPos);
            }
        }

        if (!this.computeFollowDecision(target, followPos, playerPos, distance, obstacle, false)) {
            this.resetFollowControls();
            return;
        }
        this.applyMovementControls(distance);
        if (this.autoJump.getValue() && !this.isOnStairs()) {
            this.tryJump(obstacle, followPos, playerPos);
        }
        if (this.autoClimb.getValue()) {
            this.tryClimb(targetPos, playerPos);
        }
        if (this.openDoors.getValue()) {
            this.tryInteractDoors(targetPos, playerPos);
        }
        if (this.pressButtons.getValue()) {
            this.tryPressButtons(targetPos, playerPos);
        }
    }

    public void updateFollowStateFromPatch() {
        if (mc.player == null || mc.level == null || !this.isEnabled()) {
            return;
        }
        if (this.targetName.getValue() == null || this.targetName.getValue().isBlank()) {
            return;
        }
        Player target = this.findTarget();
        if (target == null) {
            return;
        }
        Vec3 targetPos = target.position();
        Vec3 playerPos = mc.player.position();
        boolean obstacle = this.isObstacleAhead(targetPos, playerPos);
        this.computeFollowDecision(target, targetPos, playerPos, playerPos.distanceTo(targetPos), obstacle, true);
    }

    private boolean computeFollowDecision(Player target, Vec3 targetPos, Vec3 playerPos, double distance, boolean obstacle, boolean emitLogs) {
        double desiredDistance = this.followDistance.getValue().doubleValue();
        this.followShouldMove = distance > desiredDistance + 0.3;
        if (distance <= desiredDistance + 0.2) {
            this.followShouldMove = false;
            this.stuckTicks++;
            if (this.stuckTicks > 6) {
                this.resetFollowControls();
            }
            return false;
        }
        this.stuckTicks = 0;
        if (obstacle) {
            if (emitLogs) {
                // no log output
            }
        }
        if (emitLogs && mc.player.tickCount % 40 == 0) {
            // no log output
        }
        return true;
    }

    private void applyMovementControls(double distance) {
        double desiredDistance = this.followDistance.getValue().doubleValue();
        boolean shouldMove = distance > desiredDistance + 0.3;
        this.followShouldMove = shouldMove;
        mc.options.keyUp.setDown(shouldMove);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        if (mc.player.input != null) {
            mc.player.input.up = shouldMove;
            mc.player.input.down = false;
            mc.player.input.left = false;
            mc.player.input.right = false;
            mc.player.input.forwardImpulse = shouldMove ? 1.0f : 0.0f;
            mc.player.input.leftImpulse = 0.0f;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.level == null || !this.followShouldMove) {
            return;
        }
        Player target = this.findTarget();
        if (target == null) {
            return;
        }
        Vec3 playerPos = mc.player.position();
        Vec3 followTarget = this.followPath.getValue() && this.currentFollowPos != null ? this.currentFollowPos : target.position();
        double followDistance = playerPos.distanceTo(followTarget);
        boolean obstacle = this.isObstacleAhead(followTarget, playerPos);
        float forward = 1.0f;
        float strafe = 0.0f;
        if (obstacle) {
            if (this.canStepUp(playerPos)) {
                this.tryJump(true, followTarget, playerPos);
                forward = 1.0f;
                strafe = 0.0f;
                this.detourDirection = 0;
            } else {
                if (this.detourDirection == 0) {
                    this.detourDirection = this.chooseDetourDirection();
                }
                if (this.detourDirection != 0) {
                    forward = 1.0f;
                    strafe = this.detourDirection;
                    if (!this.canMoveAlongOffset(this.detourDirection * 90.0f, 1.2)) {
                        float opposite = -this.detourDirection;
                        if (this.canMoveAlongOffset(opposite * 90.0f, 1.2)) {
                            this.detourDirection = opposite;
                            strafe = opposite;
                        } else if (this.canMoveAlongOffset(this.detourDirection * 45.0f, 1.2)) {
                            strafe = this.detourDirection;
                        } else if (this.canMoveAlongOffset(-this.detourDirection * 45.0f, 1.2)) {
                            strafe = -this.detourDirection;
                            this.detourDirection = -this.detourDirection;
                        }
                    }
                } else {
                    forward = 0.0f;
                    strafe = 0.0f;
                }
            }
        } else {
            if (this.detourDirection != 0 && this.canMoveAlongOffset(0.0f, 1.2)) {
                this.detourDirection = 0;
            }
        }
        boolean shouldRunJump = followDistance > 9.0;
        event.setForward(forward);
        event.setStrafe(strafe);
        event.setSprinting(this.followShouldMove);
        if (mc.player.input != null) {
            mc.player.input.forwardImpulse = forward;
            mc.player.input.leftImpulse = strafe;
            mc.player.input.jumping = shouldRunJump && this.autoJump.getValue() && mc.player.onGround() && !this.isOnStairs();
        }
    }

    private void facePosition(Vec3 targetPos, Vec3 playerPos) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookPos = targetPos.add(0.0, mc.player.getEyeHeight() * 0.5, 0.0);
        var rotation = RotationUtil.rotationTo(eyePos, lookPos);
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float desiredYaw = rotation.getYaw();
        float desiredPitch = rotation.getPitch();
        float yawDiff = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchDiff = desiredPitch - currentPitch;
        float yawStep = Math.signum(yawDiff) * Math.min(5.0f, Math.abs(yawDiff));
        float pitchStep = Math.signum(pitchDiff) * Math.min(4.0f, Math.abs(pitchDiff));
        mc.player.setYRot(currentYaw + yawStep);
        mc.player.setXRot(currentPitch + pitchStep);
        this.lastTickForcedRotation = true;
    }

    private float chooseDetourDirection() {
        if (this.canMoveAlongOffset(-90.0f, 1.4)) {
            return -1.0f;
        }
        if (this.canMoveAlongOffset(90.0f, 1.4)) {
            return 1.0f;
        }
        if (this.canMoveAlongOffset(-45.0f, 1.4)) {
            return -1.0f;
        }
        if (this.canMoveAlongOffset(45.0f, 1.4)) {
            return 1.0f;
        }
        return 0.0f;
    }

    private float findBestDetourOffset() {
        float[] offsets = new float[]{0.0f, -45.0f, 45.0f, -90.0f, 90.0f, -135.0f, 135.0f, 180.0f};
        float bestOffset = Float.NaN;
        float bestWeight = Float.MAX_VALUE;
        for (float offset : offsets) {
            if (this.canMoveAlongOffset(offset, 1.2)) {
                float weight = Math.abs(offset);
                if (weight < bestWeight) {
                    bestWeight = weight;
                    bestOffset = offset;
                }
            }
        }
        return bestOffset;
    }

    private void updateFollowPath(Vec3 targetPos, float yaw, float pitch) {
        if (this.followPathPoints.isEmpty()) {
            this.followPathPoints.addLast(new FollowPoint(targetPos, yaw, pitch));
            return;
        }
        FollowPoint last = this.followPathPoints.getLast();
        boolean moved = last.pos.distanceToSqr(targetPos) > 0.05 * 0.05;
        boolean rotated = Math.abs(Mth.wrapDegrees(last.yaw - yaw)) > 1.0f || Math.abs(last.pitch - pitch) > 1.0f;
        if (moved || rotated) {
            this.followPathPoints.addLast(new FollowPoint(targetPos, yaw, pitch));
            while (this.followPathPoints.size() > 150) {
                this.followPathPoints.removeFirst();
            }
        }
    }

    private Vec3 getFollowPathTarget(Vec3 playerPos, Vec3 targetPos) {
        while (!this.followPathPoints.isEmpty() && playerPos.distanceTo(this.followPathPoints.peekFirst().pos) < 1.0) {
            this.followPathPoints.removeFirst();
        }
        if (this.followPathPoints.isEmpty()) {
            return targetPos;
        }
        if (!this.hasGroundSupport(this.followPathPoints.peekFirst().pos)) {
            for (FollowPoint point : this.followPathPoints) {
                if (this.hasGroundSupport(point.pos)) {
                    while (!this.followPathPoints.isEmpty() && this.followPathPoints.peekFirst() != point) {
                        this.followPathPoints.removeFirst();
                    }
                    return point.pos;
                }
            }
        }
        return this.followPathPoints.peekFirst().pos;
    }

    private void faceRotation(float desiredYaw, float desiredPitch) {
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float yawDiff = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchDiff = desiredPitch - currentPitch;
        float yawStep = Math.signum(yawDiff) * Math.min(5.0f, Math.abs(yawDiff));
        float pitchStep = Math.signum(pitchDiff) * Math.min(4.0f, Math.abs(pitchDiff));
        mc.player.setYRot(currentYaw + yawStep);
        mc.player.setXRot(currentPitch + pitchStep);
        this.lastTickForcedRotation = true;
    }

    private boolean canMoveAlongOffset(float yawOffset, double distance) {
        Vec3 direction = this.getDirectionVec(mc.player.getYRot() + yawOffset);
        Vec3 start = mc.player.position().add(0.0, 0.5, 0.0);
        Vec3 end = start.add(direction.scale(distance));
        BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return false;
        }
        Vec3 footStart = mc.player.position().add(0.0, 0.1, 0.0);
        Vec3 footEnd = footStart.add(direction.scale(distance));
        BlockHitResult footHit = mc.level.clip(new ClipContext(footStart, footEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (footHit != null && footHit.getType() != HitResult.Type.MISS) {
            return false;
        }
        Vec3 stepPos = mc.player.position().add(direction.scale(distance)).add(0.0, 1.0, 0.0);
        BlockHitResult stepHit = mc.level.clip(new ClipContext(stepPos, stepPos.add(0.0, 0.1, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return stepHit == null || stepHit.getType() == HitResult.Type.MISS;
    }

    private boolean canStepUp(Vec3 playerPos) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        float yaw = mc.player.getYRot();
        Vec3 direction = this.getDirectionVec(yaw);
        Vec3 ahead = playerPos.add(direction.scale(1.0));
        net.minecraft.core.BlockPos frontPos = new net.minecraft.core.BlockPos((int)Math.floor(ahead.x), (int)Math.floor(playerPos.y), (int)Math.floor(ahead.z));
        BlockState frontBlock = mc.level.getBlockState(frontPos);
        if (frontBlock.isAir()) {
            return false;
        }
        BlockState aboveFront = mc.level.getBlockState(frontPos.above());
        BlockState aboveFront2 = mc.level.getBlockState(frontPos.above(2));
        if (!aboveFront.isAir() || !aboveFront2.isAir()) {
            return false;
        }
        return true;
    }

    private boolean isOnStairs() {
        if (mc.player == null || mc.level == null) {
            return false;
        }
        BlockState below = mc.level.getBlockState(mc.player.blockPosition().below());
        return below.is(BlockTags.STAIRS) || below.is(BlockTags.SLABS);
    }

    private boolean hasGroundSupport(Vec3 pos) {
        if (mc.level == null) {
            return false;
        }
        net.minecraft.core.BlockPos belowPos = new net.minecraft.core.BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y) - 1, (int)Math.floor(pos.z));
        BlockState belowState = mc.level.getBlockState(belowPos);
        if (!belowState.isAir()) {
            return true;
        }
        BlockState belowState2 = mc.level.getBlockState(belowPos.below());
        return !belowState2.isAir();
    }

    private Vec3 getDirectionVec(float yaw) {
        double rad = Math.toRadians(-yaw);
        return new Vec3(Math.sin(rad), 0.0, Math.cos(rad));
    }

    private void faceTarget(Player target, Vec3 playerPos) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetEye = target.getEyePosition();
        var rotation = RotationUtil.rotationTo(eyePos, targetEye);
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float desiredYaw = rotation.getYaw();
        float desiredPitch = rotation.getPitch();
        float yawDiff = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchDiff = desiredPitch - currentPitch;
        float yawStep = Math.signum(yawDiff) * Math.min(5.0f, Math.abs(yawDiff));
        float pitchStep = Math.signum(pitchDiff) * Math.min(4.0f, Math.abs(pitchDiff));
        mc.player.setYRot(currentYaw + yawStep);
        mc.player.setXRot(currentPitch + pitchStep);
        this.lastTickForcedRotation = true;
    }

    private boolean isObstacleAhead(Vec3 targetPos, Vec3 playerPos) {
        float yaw = mc.player.getYRot();
        if (!this.manualViewControl) {
            double dx = targetPos.x - playerPos.x;
            double dz = targetPos.z - playerPos.z;
            yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        }
        float pitch = mc.player.getXRot();
        double yawRad = Math.toRadians(-yaw);
        double pitchRad = Math.toRadians(-pitch);
        Vec3 direction = new Vec3(Math.sin(yawRad) * Math.cos(pitchRad), Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(direction.scale(1.2));
        BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit != null && hit.getType() != HitResult.Type.MISS;
    }

    private void updateManualViewControl() {
        if (mc.player == null) {
            return;
        }
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float yawDiff = Math.abs(Mth.wrapDegrees(currentYaw - this.lastYaw));
        float pitchDiff = Math.abs(currentPitch - this.lastPitch);
        boolean manualMove = (yawDiff > 0.5f || pitchDiff > 0.5f) && !this.lastTickForcedRotation;
        if (manualMove) {
            this.manualViewControl = true;
            this.manualViewControlTicks = 20;
        } else if (this.manualViewControl && this.manualViewControlTicks > 0) {
            this.manualViewControlTicks--;
            if (this.manualViewControlTicks == 0) {
                this.manualViewControl = false;
            }
        }
        this.lastYaw = currentYaw;
        this.lastPitch = currentPitch;
        this.lastTickForcedRotation = false;
    }

    private void tryJump(boolean obstacle, Vec3 targetPos, Vec3 playerPos) {
        if (mc.player == null) {
            return;
        }
        if (obstacle && mc.player.onGround()) {
            mc.options.keyJump.setDown(true);
            this.jumpTicks = 5;
            return;
        }
        if (this.jumpTicks > 0) {
            mc.options.keyJump.setDown(true);
            this.jumpTicks--;
            return;
        }
        if (mc.player.onGround()) {
            mc.options.keyJump.setDown(false);
        }
    }

    private void resetFollowControls() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        this.followShouldMove = false;
        this.detourDirection = 0;
        if (mc.player.input != null) {
            mc.player.input.up = false;
            mc.player.input.down = false;
            mc.player.input.left = false;
            mc.player.input.right = false;
            mc.player.input.forwardImpulse = 0.0f;
            mc.player.input.leftImpulse = 0.0f;
            mc.player.input.jumping = false;
        }
    }

    private void tryClimb(Vec3 targetPos, Vec3 playerPos) {
        if (mc.player == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(mc.player.blockPosition().below());
        if (state.is(Blocks.LADDER)) {
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(false);
        } else {
            mc.options.keyUp.setDown(false);
        }
    }

    private void tryInteractDoors(Vec3 targetPos, Vec3 playerPos) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (Math.abs(targetPos.x - playerPos.x) < 1.5 && Math.abs(targetPos.z - playerPos.z) < 1.5) {
            return;
        }
        BlockHitResult hit = this.traceBlockInFront();
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockState state = mc.level.getBlockState(hit.getBlockPos());
            if (state.getBlock() == Blocks.OAK_DOOR || state.getBlock() == Blocks.IRON_DOOR || state.getBlock() == Blocks.SPRUCE_DOOR || state.getBlock() == Blocks.BIRCH_DOOR || state.getBlock() == Blocks.JUNGLE_DOOR || state.getBlock() == Blocks.ACACIA_DOOR || state.getBlock() == Blocks.DARK_OAK_DOOR || state.getBlock() == Blocks.MANGROVE_DOOR || state.getBlock() == Blocks.CHERRY_DOOR) {
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            }
        }
    }

    private void tryPressButtons(Vec3 targetPos, Vec3 playerPos) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        BlockHitResult hit = this.traceBlockInFront();
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockState state = mc.level.getBlockState(hit.getBlockPos());
            if (state.getBlock() == Blocks.STONE_BUTTON || state.getBlock() == Blocks.OAK_BUTTON || state.getBlock() == Blocks.SPRUCE_BUTTON || state.getBlock() == Blocks.BIRCH_BUTTON || state.getBlock() == Blocks.JUNGLE_BUTTON || state.getBlock() == Blocks.ACACIA_BUTTON || state.getBlock() == Blocks.DARK_OAK_BUTTON || state.getBlock() == Blocks.POLISHED_BLACKSTONE_BUTTON) {
                mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            }
        }
    }

    private BlockHitResult traceBlockInFront() {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(mc.player.getLookAngle().scale(3.0));
        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    private Player findTarget() {
        if (mc.level == null) {
            return null;
        }
        String target = this.targetName.getValue();
        return mc.level.players().stream()
                .filter(Objects::nonNull)
                .filter(player -> player != mc.player)
                .filter(player -> player.getName().getString().equalsIgnoreCase(target))
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(mc.player)))
                .orElse(null);
    }
}
