package eu.deltatimo.minecraft.elytrahud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

public class ElytraFlightHud implements ClientModInitializer {
    public static class DrawLineArguments {
        public float x;
        public float y;
        public float x2;
        public float y2;
        public float r = 1.0f;
        public float g = 1.0f;
        public float b = 1.0f;
        public float a = 1.0f;
        public float r2 = 1.0f;
        public float g2 = 1.0f;
        public float b2 = 1.0f;
        public float a2 = 1.0f;

        public DrawLineArguments(float x, float y, float x2, float y2) {
            position(x, y, x2, y2);
        }

        public DrawLineArguments(float x, float y, float x2, float y2, float r, float g, float b) {
            position(x, y, x2, y2).color(r, g, b);
        }

        public DrawLineArguments(float x, float y, float x2, float y2, float r, float g, float b, float a) {
            position(x, y, x2, y2).color(r, g, b, a);
        }

        public DrawLineArguments position(float x, float y, float x2, float y2) {
            this.x = x;
            this.y = y;
            this.x2 = x2;
            this.y2 = y2;
            return this;
        }

        public DrawLineArguments color(float r, float g, float b) {
            return color_start(r, g, b).color_end(r, g, b);
        }

        public DrawLineArguments color(float r, float g, float b, float a) {
            return color_start(r, g, b, a).color_end(r, g, b, a);
        }

        public DrawLineArguments alpha(float a) {
            return alpha_start(a).alpha_end(a);
        }

        public DrawLineArguments color_start(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
            return this;
        }

        public DrawLineArguments color_start(float r, float g, float b, float a) {
            return color_start(r, g, b).alpha_start(a);
        }

        public DrawLineArguments alpha_start(float a) {
            this.a = a;
            return this;
        }

        public DrawLineArguments color_end(float r, float g, float b) {
            this.r2 = r;
            this.g2 = g;
            this.b2 = b;
            return this;
        }

        public DrawLineArguments color_end(float r, float g, float b, float a) {
            return color_end(r, g, b).alpha_end(a);
        }

        public DrawLineArguments alpha_end(float a) {
            this.a2 = a;
            return this;
        }

        public static DrawLineArguments make(float x, float y, float x2, float y2) {
            return new DrawLineArguments(x, y, x2, y2);
        }
    }

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LogManager.getLogger("elytra-flight-hud");

    private boolean hudEnabled = true;

    @Override
    public void onInitializeClient() {

        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        HudRenderCallback.EVENT.register(this::onHudRender);

        var keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.elytra_flight_hud.toggle", InputUtil.Type.KEYSYM, InputUtil.GLFW_KEY_U, "category.elytra_flight_hud.general"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyToggle.wasPressed()) {
                hudEnabled = !hudEnabled;
                if (client.player != null) {
                    client.player.sendMessage(hudEnabled ? Text.translatable("hud.elytra_flight_hud.hud_enabled") : Text.translatable("hud.elytra_flight_hud.hud_disabled"), true);
                }
            }
        });

        LOGGER.info("Elytra Flight Hud initialized!");
    }

    private static final float GRAVITY = -0.0784f;

    private float hud_alpha = 0.0f;

    private float air_speed_old = 0;

    private float acceleration = 0;

    private void onHudRender(DrawContext drawContext, float tickDelta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        double aspect = (double) window.getWidth() / (double) window.getHeight();

        int screenWidth = window.getScaledWidth();
        int screenHeight = window.getScaledHeight();
        int screenLesser = Math.min(screenWidth, screenHeight);
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;
        int maincolor = 0x00FF00; // remember that this should also be usable at night at some point. affects only text

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        Camera camera = gameRenderer.getCamera();
        double camera_pitch_deg = camera.getPitch();
        double camera_pitch = Math.toRadians(camera_pitch_deg);

        assert client.player != null;
        double fov_mult = client.player.getFovMultiplier();
        double fov_deg = client.options.getFov().getValue() * fov_mult;
        double fov = Math.toRadians(fov_deg);
        double fov_tan = Math.tan(fov / 2);

        float pixels_half = (float) screenHeight / 2;

        float horizon_width = screenLesser / 6f;
        float ladder_width = screenLesser / 16f; // no longer the width overall(!)
        float ladder_text = ladder_width + screenLesser / 65f;
        float horizon_gap = screenLesser / 65f;
        float horizon_vertical_blip_length = screenHeight / 160f;
        float center_height = screenCenterY + (float) (Math.tan(-camera_pitch) / fov_tan) * pixels_half;

        ClientPlayerEntity player = client.player;
        if (player != null) {
            float hud_alpha_target = player.isFallFlying() ? 1.0f : 0.0f;
            if (!hudEnabled) {
                hud_alpha_target = 0.0f;
            }
            // float hud_alpha_target = 1f;
            hud_alpha = Math.max(0f, Math.min(1f, hud_alpha + Math.signum(hud_alpha_target - hud_alpha) * tickDelta * 0.1f));
            if (hud_alpha >= 0.0001) {
                Vec3d player_pos = player.getPos();
                // Vec3f velocity = new Vec3f(player.getVelocity());
                // velocity.rotate(player.getMovementDirection().getRotationQuaternion());
                Vec3d velocity = player.getVelocity().rotateY((float) Math.toRadians(camera.getYaw())).rotateX((float) Math.toRadians(camera.getPitch()));
                // double upwardSpeed = velocity.getY();
                double upwardSpeed = velocity.getY();
                double forwardSpeed = velocity.getZ();
                double sidewaysSpeed = -velocity.getX();
                double upwardSpeedAngle = upwardSpeed / forwardSpeed;
                double rightSpeedAngle = sidewaysSpeed / forwardSpeed;
                float flight_vector_x = screenCenterX + (float) (rightSpeedAngle / fov_tan) * pixels_half;
                float flight_vector_y = screenCenterY - (float) (upwardSpeedAngle / fov_tan) * pixels_half;
                float drift;
                if (Math.abs(screenCenterY - flight_vector_y) < 0.8f * pixels_half) {
                    drift = (float) (rightSpeedAngle / fov_tan) * pixels_half;
                } else {
                    drift = 0;
                }

                int radar_height = 0;
                BlockPos player_blockpos = player.getBlockPos();
                if (client.world != null) {
                    for (int y = player.getBlockPos().getY() - 1; y >= client.world.getBottomY() && client.world.isAir(new BlockPos(player_blockpos.getX(), y, player_blockpos.getZ())); y--) {
                        ++radar_height;
                    }
                }

                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.enableBlend();

                // MatrixStack modelviewstack = RenderSystem.getModelViewStack();
                // modelviewstack.push();
                // RenderSystem.applyModelViewMatrix();
                Matrix4f matrix4f = drawContext.getMatrices().peek().getPositionMatrix();
                Matrix3f matrix3f = drawContext.getMatrices().peek().getNormalMatrix();
                // Matrix4f matrix4f = modelviewstack.peek().getPositionMatrix();
                ///
                GlStateManager._depthMask(false);
                GlStateManager._disableCull();
                RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder bufferBuilder = tessellator.getBuffer();
                RenderSystem.lineWidth(2.0F);
                bufferBuilder.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

                Consumer<DrawLineArguments> drawLine = a -> {
                    float normal_x = a.x2 - a.x;
                    float normal_y = a.y2 - a.y;
                    float length = (float) Math.sqrt(normal_x * normal_x + normal_y * normal_y);
                    if (Math.abs(length) < 0.0001) return;
                    normal_x /= length;
                    normal_y /= length;
                    bufferBuilder.vertex(matrix4f, a.x, a.y, -90.0f).color(a.r, a.g, a.b, a.a).normal(matrix3f, normal_x, normal_y, 0f).next();
                    bufferBuilder.vertex(matrix4f, a.x2, a.y2, -90.0f).color(a.r2, a.g2, a.b2, a.a2).normal(matrix3f, normal_x, normal_y, 0f).next();
                };

                // draw lines on the horizon UPWARDS. (-degrees in minecraft)
                for (int i = -80; i < 0; i += 10) {
                    // Out of upper screen bound. next!
                    // if (i < screen_upper_pitch_deg) continue;
                    double diff_deg = i - camera_pitch_deg; // -90 - -30 = -90 + 30 = -60
                    float diff_pixels = (float) (Math.tan(Math.toRadians(diff_deg)) / fov_tan) * pixels_half;
                    float height = screenCenterY + diff_pixels;
                    if (height > screenHeight * 0.85f || height < screenHeight * 0.15f) continue;

                    drawLine.accept(DrawLineArguments.make(screenCenterX - horizon_gap - ladder_width + drift, height, screenCenterX - horizon_gap + drift, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(screenCenterX - horizon_gap - ladder_width + drift, height, screenCenterX - horizon_gap - ladder_width + drift, height + horizon_vertical_blip_length).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(screenCenterX + horizon_gap + ladder_width + drift, height, screenCenterX + horizon_gap + drift, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(screenCenterX + horizon_gap + ladder_width + drift, height, screenCenterX + horizon_gap + ladder_width + drift, height + horizon_vertical_blip_length).color(0f, 1f, 0f, hud_alpha));
                }

                // draw lines on the horizon DOWNWARDS. (+degrees in minecraft)
                for (int i = 80; i > 0; i -= 10) {
                    // Out of lower screen bound. next!
                    // if (i < screen_upper_pitch_deg) continue;
                    double diff_deg = i - camera_pitch_deg; // -90 - -30 = -90 + 30 = -60
                    float diff_pixels = (float) (Math.tan(Math.toRadians(diff_deg)) / fov_tan) * pixels_half;
                    float height = screenCenterY + diff_pixels;
                    if (height > screenHeight * 0.85f || height < screenHeight * 0.15f) continue;


                    // left horizontal lines
                    float leftx = screenCenterX - horizon_gap - ladder_width + drift;
                    float part_width = ladder_width / 3f;
                    drawLine.accept(DrawLineArguments.make(leftx, height, leftx + part_width - part_width / 3, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(leftx + part_width, height, leftx + 2 * part_width - part_width / 3, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(leftx + 2 * part_width, height, leftx + 3 * part_width, height).color(0f, 1f, 0f, hud_alpha));
                    // right horizontal lines
                    float rightx = screenCenterX + horizon_gap + ladder_width + drift;
                    drawLine.accept(DrawLineArguments.make(rightx, height, rightx - part_width + part_width / 3, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(rightx - part_width, height, rightx - 2 * part_width + part_width / 3, height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(rightx - 2 * part_width, height, rightx - 3 * part_width, height).color(0f, 1f, 0f, hud_alpha));
                    // vertical ends
                    drawLine.accept(DrawLineArguments.make(screenCenterX - horizon_gap - ladder_width + drift, height, screenCenterX - horizon_gap - ladder_width + drift, height - horizon_vertical_blip_length).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(screenCenterX + horizon_gap + ladder_width + drift, height, screenCenterX + horizon_gap + ladder_width + drift, height - horizon_vertical_blip_length).color(0f, 1f, 0f, hud_alpha));
                }

                if (!(center_height > screenHeight * 0.85f || center_height < screenHeight * 0.15f)) {
                    drawLine.accept(DrawLineArguments.make(screenCenterX - horizon_gap + drift, center_height, screenCenterX - horizon_gap - horizon_width + drift, center_height).color(0f, 1f, 0f, hud_alpha));
                    drawLine.accept(DrawLineArguments.make(screenCenterX + horizon_gap + drift, center_height, screenCenterX + horizon_gap + horizon_width + drift, center_height).color(0f, 1f, 0f, hud_alpha));
                }

                // float flight_vector_size = screenLesser / 100f;
                float flight_vector_size = screenLesser / 70f;
                float flight_vector_radius = flight_vector_size / 2f;
                // drawLine.accept(DrawLineArguments.make(flight_vector_x - flight_vector_size / 2f, flight_vector_y - flight_vector_size / 2f, flight_vector_x + flight_vector_size / 2f, flight_vector_y - flight_vector_size / 2f).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x + flight_vector_size / 2f, flight_vector_y - flight_vector_size / 2f, flight_vector_x + flight_vector_size / 2f, flight_vector_y + flight_vector_size / 2f).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x + flight_vector_size / 2f, flight_vector_y + flight_vector_size / 2f, flight_vector_x - flight_vector_size / 2f, flight_vector_y + flight_vector_size / 2f).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x - flight_vector_size / 2f, flight_vector_y + flight_vector_size / 2f, flight_vector_x - flight_vector_size / 2f, flight_vector_y - flight_vector_size / 2f).color(0f, 1f, 0f, 1f * hud_alpha));

                for (DrawLineArguments line : circleLines(flight_vector_x, flight_vector_y, flight_vector_radius, 10)) {
                    drawLine.accept(line.color(0f, 1f, 0f, hud_alpha));
                }
                // drawLine.accept(DrawLineArguments.make(flight_vector_x, flight_vector_y - flight_vector_size/2f, flight_vector_x, flight_vector_y - flight_vector_size/2f - flight_vector_size*0.4f).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x - flight_vector_size/2f - flight_vector_size*0.5f, flight_vector_y, flight_vector_x - flight_vector_size/2f, flight_vector_y).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x + flight_vector_size/2f, flight_vector_y, flight_vector_x + flight_vector_size/2f + flight_vector_size*0.5f, flight_vector_y).color(0f, 1f, 0f, 1f * hud_alpha));
                float elytra_roll_deg = forwardSpeed < 0.0001f ? 0f : (float) (Math.min(2, Math.max(-2, sidewaysSpeed / forwardSpeed))) * -45f;
                float elytra_roll = (float) Math.toRadians(elytra_roll_deg);
                drawLine.accept(DrawLineArguments.make(
                        flight_vector_x + (float) Math.sin(elytra_roll) * flight_vector_radius,
                        flight_vector_y - (float) Math.cos(elytra_roll) * flight_vector_radius,
                        flight_vector_x + (float) Math.sin(elytra_roll) * (flight_vector_radius + flight_vector_size * 0.4f),
                        flight_vector_y - (float) Math.cos(elytra_roll) * (flight_vector_radius + flight_vector_size * 0.4f)
                ).color(0f, 1f, 0f, hud_alpha));

                drawLine.accept(DrawLineArguments.make(
                        flight_vector_x + (float) Math.sin(elytra_roll + 0.5 * Math.PI) * flight_vector_radius,
                        flight_vector_y - (float) Math.cos(elytra_roll + 0.5 * Math.PI) * flight_vector_radius,
                        flight_vector_x + (float) Math.sin(elytra_roll + 0.5 * Math.PI) * (flight_vector_radius + flight_vector_size * 0.5f),
                        flight_vector_y - (float) Math.cos(elytra_roll + 0.5 * Math.PI) * (flight_vector_radius + flight_vector_size * 0.5f)
                ).color(0f, 1f, 0f, hud_alpha));

                drawLine.accept(DrawLineArguments.make(
                        flight_vector_x + (float) Math.sin(elytra_roll - 0.5 * Math.PI) * flight_vector_radius,
                        flight_vector_y - (float) Math.cos(elytra_roll - 0.5 * Math.PI) * flight_vector_radius,
                        flight_vector_x + (float) Math.sin(elytra_roll - 0.5 * Math.PI) * (flight_vector_radius + flight_vector_size * 0.5f),
                        flight_vector_y - (float) Math.cos(elytra_roll - 0.5 * Math.PI) * (flight_vector_radius + flight_vector_size * 0.5f)
                ).color(0f, 1f, 0f, hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x - flight_vector_size/2f - flight_vector_size*0.5f, flight_vector_y, flight_vector_x - flight_vector_size/2f, flight_vector_y).color(0f, 1f, 0f, 1f * hud_alpha));
                // drawLine.accept(DrawLineArguments.make(flight_vector_x + flight_vector_size/2f, flight_vector_y, flight_vector_x + flight_vector_size/2f + flight_vector_size*0.5f, flight_vector_y).color(0f, 1f, 0f, 1f * hud_alpha));

                // drawLine.accept(DrawLineArguments.make(screenCenterX, screenCenterY, flight_vector_x, flight_vector_y).color(0f, 1f, 0f, 1f));
                double flight_vector_angle = Math.atan2(screenCenterY - flight_vector_y, screenCenterX - flight_vector_x);

                if ((Math.abs(screenCenterY - flight_vector_y) > 0.8f * pixels_half) | (Math.abs(screenCenterX - flight_vector_x) > 0.8f * pixels_half)) {
                    drawLine.accept(DrawLineArguments.make(screenCenterX, screenCenterY, flight_vector_x, flight_vector_y).color(0f, 1f, 0f, hud_alpha));
                }
                // drawLine.accept(DrawLineArguments.make(screenCenterX - ladder_width/2f, (float) screenCenterY, screenCenterX - ladder_width/2f + ladder_width/3, (float) screenCenterY).color(0f, 1f, 0f, 1f));
                // drawLine.accept(DrawLineArguments.make(screenCenterX - ladder_width/2f + 2*ladder_width/3f, (float) screenCenterY, screenCenterX - ladder_width/2f + ladder_width, (float) screenCenterY).color(0f, 1f, 0f, 1f));

                // bufferBuilder.vertex(matrix4f, screenCenterX - screenWidth/4.0f, screenCenterY - screenHeight/4.0f, -90.0f).color(0, 255, 0, 255).normal(matrix3f, 1.0F, 1.0F, 0.0F).next();
                // bufferBuilder.vertex(matrix4f, screenCenterX + screenWidth/4.0f, screenCenterY - screenHeight/4.0f, -90.0f).color(0, 255, 0, 255).normal(matrix3f, 1.0F, 1.0F, 0.0F).next();
                // bufferBuilder.vertex(0.0D, 0.0D, -90.0D).color(0, 255, 0, 255).normal(0.0F, 0.0F, 1.0F).next();
                // bufferBuilder.vertex((double)screenCenterX, (double)screenCenterY, -90.0D).color(0, 255, 0, 255).normal(0.0F, 0.0F, 1.0F).next();

                // Compass

                float heading = realMod(camera.getYaw(), 360);
                float compass_width = ladder_width * 2 + horizon_gap * 2;
                float compass_blip_height = screenHeight / 200f;

                for (int i = 360; i > 0; i -= 5) {
                    // Skip first blip if it's outside.
                    float heading_offset = (realMod(i - heading + 360,360) - 180);
                    if (Math.abs(heading_offset) > 15) continue;
                    float heading_x = screenCenterX + (heading_offset / 15f) * compass_width / 2f;
                    float font_height = textRenderer.fontHeight;
                    String text = String.format("%02.0f", (float) i/10); // i'm too stupid; there should not be float here?
                    drawLine.accept(DrawLineArguments.make(heading_x, screenCenterY + screenHeight / 4f + font_height * 1.05f, heading_x, screenCenterY + screenHeight / 4f + font_height * 1.05f + compass_blip_height).color(0f, 1f, 0f, hud_alpha));
                }

                float compass_triangle_y = screenCenterY + screenHeight / 4f + textRenderer.fontHeight * 1.05f + compass_blip_height * 0.95f;
                float compass_triangle_height = compass_blip_height;
                float compass_triangle_width = compass_triangle_height * 2f;

                drawLine.accept(DrawLineArguments.make((float) screenCenterX, compass_triangle_y, screenCenterX + compass_triangle_width / 2f, compass_triangle_y + compass_triangle_height).color(0f, 1f, 0f, hud_alpha));
                drawLine.accept(DrawLineArguments.make((float) screenCenterX + compass_triangle_width / 2f, compass_triangle_y + compass_triangle_height, screenCenterX - compass_triangle_width / 2f, compass_triangle_y + compass_triangle_height).color(0f, 1f, 0f, hud_alpha));
                drawLine.accept(DrawLineArguments.make((float) screenCenterX - compass_triangle_width / 2f, compass_triangle_y + compass_triangle_height, screenCenterX, compass_triangle_y).color(0f, 1f, 0f, hud_alpha));
                // bufferBuilder.vertex(matrix4f, screenCenterX + compass_triangle_width/2f, compass_triangle_y + compass_triangle_height, -90f).color(0f, 1f, 0f, 1f * hud_alpha).next();
                // bufferBuilder.vertex(matrix4f, screenCenterX - compass_triangle_width/2f, compass_triangle_y + compass_triangle_height, -90f).color(0f, 1f, 0f, 1f * hud_alpha).next();

                // bufferBuilder.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
                // bufferBuilder.vertex(matrix4f, screenCenterX, compass_triangle_y, -90f).color(0f, 1f, 0f, 1f * hud_alpha).next();
                // bufferBuilder.vertex(matrix4f, screenCenterX + compass_triangle_width/2f, compass_triangle_y + compass_triangle_height, -90f).color(0f, 1f, 0f, 1f * hud_alpha).next();
                // bufferBuilder.vertex(matrix4f, screenCenterX - compass_triangle_width/2f, compass_triangle_y + compass_triangle_height, -90f).color(0f, 1f, 0f, 1f * hud_alpha).next();

                tessellator.draw();

                RenderSystem.lineWidth(1.0F);

                Vec3d player_velocity_vector = new Vec3d(player.getVelocity().x, player.getVelocity().y - GRAVITY, player.getVelocity().z);
                float air_speed = (float) (player_velocity_vector.length()) * 20f * 3.6f;
                acceleration = acceleration * 0.96f + 0.04f * (air_speed - air_speed_old) * 20f;

                if (hud_alpha > 0.66) {
                    for (int i = -80; i <= 80; i += 10) {
                        if (i == 0) continue;
                        // Out of upper screen bound. next!
                        // if (i < screen_upper_pitch_deg) continue;
                        double diff_deg = i - camera_pitch_deg; // -90 - -30 = -90 + 30 = -60
                        float diff_pixels = (float) (Math.tan(Math.toRadians(diff_deg)) / fov_tan) * pixels_half;
                        // float height = screenCenterY + diff_pixels - screenHeight / 160f;
                        float height = screenCenterY + diff_pixels - (i > 0 ? (0.9f * textRenderer.fontHeight) : (textRenderer.fontHeight * (0.15f)));
                        if (height > screenHeight * 0.85f || height < screenHeight * 0.15f) continue;
                        String text = "" + Math.abs(i);
                        drawContext.drawText(textRenderer, text, (int) (screenCenterX + ladder_text + drift), (int) height, maincolor, false);
                        drawContext.drawText(textRenderer, text, (int) (screenCenterX - ladder_text - textRenderer.getWidth(text) - screenWidth / 500f + drift), (int) height, maincolor, false);
                    }
                    for (int i = 360; i > 0; i -= 10) {
                        // Skip first blip if it's outside.
                        float heading_offset = (realMod(i - heading + 360,360) - 180);
                        if (Math.abs(heading_offset) > 15) continue;
                        float heading_x = screenCenterX + (heading_offset / 15f) * compass_width / 2f;
                        String text = String.format("%02.0f", (float) i/10); // i'm too stupid; there should not be float here?
                        drawContext.drawText(textRenderer, text, (int) (heading_x - textRenderer.getWidth(text) / 2), (int) (screenCenterY + screenHeight / 4f), maincolor, false);
                    }

                    drawContext.drawText(textRenderer, "" + ((int) Math.floor(player_pos.y)), (int) (screenCenterX + horizon_width * 1.2f), screenCenterY, maincolor, false);
                    // textRenderer.draw(stack, "" + (Math.round((float) player_velocity_vector.y * 10f)), screenCenterX + ladder_width * 0.8f, (float) screenCenterY + textRenderer.fontHeight * 1.5f, maincolor);
                    int fall_distance_color = maincolor;
                    if (player.fallDistance > player.getSafeFallDistance() * 2f) {
                        fall_distance_color = 0xFF0000;
                    } else if (player.fallDistance > player.getSafeFallDistance()) {
                        fall_distance_color = 0xFF8800;
                    } else if (player.fallDistance > player.getSafeFallDistance() * 0.75f) {
                        fall_distance_color = 0xFFFF00;
                    }
                    // vertical speed is shown in m/s while airspeed is in km/h
                    drawContext.drawText(textRenderer, "" + (String.format("%+.1f",(float) player.getVelocity().getY() * 20f)), (int) (screenCenterX + horizon_width * 1.2f), (int) ((float) screenCenterY + textRenderer.fontHeight * 1.5f), fall_distance_color, false);
                    drawContext.drawText(textRenderer, radar_height + "R", (int) (screenCenterX + horizon_width * 1.2f), screenCenterY + screenHeight / 8, maincolor, false);

                    // if (air_speed < 0.01) air_speed = 0;

                    int air_speed_color = maincolor;
                    double collisionDamage = collisionDamageHorizontal(player);
                    if (collisionDamage > 15) {
                        air_speed_color = 0xFF0000;
                    } else if (collisionDamage > 5) {
                        air_speed_color = 0xFFFF00;
                    }
                    drawContext.drawText(textRenderer, "" + String.format("%3.0f",air_speed), (int) (screenCenterX - horizon_width * 1.5), screenCenterY, air_speed_color, false); // km/h
                    drawContext.drawText(textRenderer, "" + String.format("%+3.0f",acceleration), (int) (screenCenterX - horizon_width * 1.5), screenCenterY + (int) ((float) textRenderer.fontHeight * 1.5f), air_speed_color, false); // km/h/s

                    air_speed_old = air_speed;

                    GlStateManager._enableCull();
                    GlStateManager._depthMask(true);
                }
            }
        }
    }
    private float realMod(float a, float b) {
        float result = a % b;
        return result < 0 ? result + b : result;
    }

    private Collection<DrawLineArguments> circleLines(float x, float y, float radius, int parts) {
        ArrayList<DrawLineArguments> lines = new ArrayList<>(parts);
        final double TWO_PI = Math.PI * 2D;
        for (double phi = 0D; phi < TWO_PI; phi += TWO_PI / parts) {
            double x1 = x + Math.cos(phi) * radius;
            double x2 = x + Math.cos(phi + TWO_PI / parts) * radius;
            double y1 = y + Math.sin(phi) * radius;
            double y2 = y + Math.sin(phi + TWO_PI / parts) * radius;
            lines.add(DrawLineArguments.make((float) x1, (float) y1, (float) x2, (float) y2));
        }
        return lines;
    }

    private double collisionDamageHorizontal(PlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        Vec3d multiplied_velocity = velocity.multiply(0.99f, 0.98f, 0.99f);
        return multiplied_velocity.horizontalLength() * 10.0 - player.getSafeFallDistance();
    }

    private void renderLine(float x, float y, float x2, float y2, float r, float g, float b, float a) {
    }
}
