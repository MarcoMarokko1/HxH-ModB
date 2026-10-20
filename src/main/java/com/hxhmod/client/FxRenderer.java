package com.hxhmod.client;

import com.hxhmod.nen.FxEntity;
import com.hxhmod.nen.FxPath;
import com.hxhmod.nen.NenType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die 3D-Effekt-Modelle leuchtend (additive "Augen"-Render-Art) in der Aura-Farbe des Charakters.
 * Alle Animationen werden hier prozedural aus der Zeit berechnet.
 */
public class FxRenderer extends EntityRenderer<FxEntity> {
    private static final int FULL = 15728880;

    private final ModelPart head, jaw, seg, fist, palm, blade, bar;
    private final ModelPart[] fingers = new ModelPart[5];

    public FxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(FxModel.LAYER);
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        seg = root.getChild("seg");
        fist = root.getChild("fist");
        palm = root.getChild("palm");
        for (int i = 0; i < 5; i++) fingers[i] = palm.getChild("finger" + i);
        blade = root.getChild("blade");
        bar = root.getChild("bar");
    }

    @Override
    public ResourceLocation getTextureLocation(FxEntity e) {
        return FxModel.TEXTURE;
    }

    @Override
    public boolean shouldRender(FxEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(FxEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        NenType t = NenType.byOrdinal(e.nen);
        if (t == null || e.life <= 0) return;
        float age = e.tickCount + pt;
        float a = Math.min(1f, Math.min(age / 2f, (e.life - age) / (e.life * 0.25f + 1f)));
        if (a <= 0f) return;
        float[] c = {Math.min(1f, t.r * 0.7f + 0.3f), Math.min(1f, t.g * 0.7f + 0.3f), Math.min(1f, t.b * 0.7f + 0.3f)};
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(FxModel.TEXTURE));
        Vec3 here = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float u = Mth.clamp(age / (float) e.life, 0f, 1f);
        switch (e.kind) {
            case FxEntity.DRAGON -> dragon(e, ps, vc, here, age, c, a);
            case FxEntity.FIST -> fist(e, ps, vc, here, age, c, a);
            case FxEntity.PALM -> palm(e, ps, vc, here, age, u, c, a);
            case FxEntity.SCISSORS -> scissors(e, ps, vc, here, age, c, a);
            case FxEntity.CLAWS -> claws(e, ps, vc, here, u, c, a);
            case FxEntity.BOLT -> bolt(e, ps, vc, here, age, c, a);
            case FxEntity.TORNADO -> tornado(e, ps, vc, here, age, c, a);
            case FxEntity.DOME -> dome(e, ps, vc, here, u, c, a);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Zeichen-Helfer

    private static Vec3 safe(Vec3 d) {
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private void draw(PoseStack ps, VertexConsumer vc, ModelPart part, Vec3 off, Vec3 dirIn, Vec3 local, float s,
                      float[] c, float a, float roll, float extra) {
        Vec3 dir = safe(dirIn);
        ps.pushPose();
        ps.translate(off.x, off.y, off.z);
        double hor = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(-dir.x, -dir.z)));
        ps.mulPose(Axis.XP.rotation((float) Math.atan2(dir.y, hor)));
        ps.translate(local.x, local.y, local.z);
        if (roll != 0f) ps.mulPose(Axis.ZP.rotation(roll));
        if (extra != 0f) ps.mulPose(Axis.XP.rotation(extra));
        ps.scale(s, s, s);
        ps.scale(-1f, -1f, 1f);
        part.render(ps, vc, FULL, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], a);
        ps.popPose();
    }

    /** Gestreckter Stab von `from` nach `to` mit Dicke in Blöcken. */
    private void bar(PoseStack ps, VertexConsumer vc, Vec3 from, Vec3 to, float thickness, float[] c, float a) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 mid = from.add(d.scale(0.5));
        ps.pushPose();
        ps.translate(mid.x, mid.y, mid.z);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(-dir.x, -dir.z)));
        ps.mulPose(Axis.XP.rotation((float) Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
        ps.scale(thickness * 16f, thickness * 16f, (float) len);
        ps.scale(-1f, -1f, 1f);
        bar.render(ps, vc, FULL, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], a);
        ps.popPose();
    }

    private void ring(PoseStack ps, VertexConsumer vc, Vec3 base, double radius, double y, int n, float thickness,
                      float[] c, float a, double phase) {
        Vec3 prev = null;
        Vec3 first = null;
        for (int i = 0; i <= n; i++) {
            double ang = phase + Math.PI * 2 * i / n;
            Vec3 p = base.add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
            if (prev != null) bar(ps, vc, prev, p, thickness, c, a);
            if (first == null) first = p;
            prev = p;
        }
    }

    // ------------------------------------------------------------------ Drache

    private void dragon(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float[] c, float a) {
        double segTicks = 0.32 * e.scale / e.speedHint();
        for (int i = 18; i >= 1; i--) {
            double tt = age - i * segTicks;
            Vec3 sp = FxPath.position(e, tt);
            Vec3 sd = FxPath.tangent(e, tt);
            float s = e.scale * Math.max(0.35f, 1f - i * 0.035f);
            float roll = Mth.sin(age * 0.35f + i * 0.6f) * 0.35f;
            draw(ps, vc, seg, sp.subtract(here), sd, Vec3.ZERO, s, c, a * Math.max(0.25f, 1f - i * 0.03f), roll, 0f);
        }
        Vec3 hp = FxPath.position(e, age);
        Vec3 hd = FxPath.tangent(e, age);
        jaw.xRot = 0.35f + 0.3f * Mth.sin(age * 0.9f);
        draw(ps, vc, head, hp.subtract(here), hd, Vec3.ZERO, e.scale * 1.15f, c, a, 0f, 0f);
    }

    // ------------------------------------------------------------------ Faust (Gon)

    private void fist(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float[] c, float a) {
        Vec3 dir = safe(e.p1.subtract(e.p0));
        float hold = e.f0 * e.life;
        float g = hold > 0f ? Mth.clamp(age / hold, 0f, 1f) : 1f;
        float s = e.scale * (0.35f + 0.65f * g * g * (3f - 2f * g));
        Vec3 pos = FxPath.position(e, age);
        if (age < hold) {
            pos = pos.add(Mth.sin(age * 37f) * 0.05, Mth.cos(age * 29f) * 0.05, Mth.sin(age * 23f) * 0.05);
        }
        draw(ps, vc, fist, pos.subtract(here), dir, Vec3.ZERO, s, c, a, 0f, 0f);
        float strikeEnd = e.f1 * e.life;
        if (age >= hold && age < strikeEnd + 4f) {
            for (int k = 1; k <= 3; k++) {
                Vec3 gp = FxPath.position(e, age - k * 0.9);
                draw(ps, vc, fist, gp.subtract(here), dir, Vec3.ZERO, s * (1f - 0.08f * k), c, a * 0.35f / k, 0f, 0f);
            }
        }
    }

    // ------------------------------------------------------------------ Offene Hand (Pa)

    private void palm(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float u, float[] c, float a) {
        Vec3 dir = safe(e.p1.subtract(e.p0));
        float spread = Mth.clamp(age / (e.life * 0.4f), 0f, 1f);
        for (int i = 0; i < 5; i++) {
            fingers[i].yRot = (i - 2) * 0.32f * spread;
            fingers[i].xRot = -0.15f * spread * (Math.abs(i - 2) == 2 ? 1.6f : 1f);
        }
        float s = e.scale * (0.8f + 0.9f * u);
        draw(ps, vc, palm, FxPath.position(e, age).subtract(here), dir, Vec3.ZERO, s, c, a, 0f, Mth.HALF_PI);
        for (int k = 1; k <= 2; k++) {
            draw(ps, vc, palm, FxPath.position(e, age - k * 1.2).subtract(here), dir, Vec3.ZERO, s * (1f - 0.1f * k), c, a * 0.3f / k, 0f, Mth.HALF_PI);
        }
    }

    // ------------------------------------------------------------------ Schere (Choki)

    private void scissors(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float[] c, float a) {
        Vec3 dir = safe(e.p1.subtract(e.p0));
        float spin = age * 0.55f;
        float open = 0.45f + 0.2f * Mth.sin(age * 1.3f);
        for (int echo = 0; echo < 3; echo++) {
            Vec3 pos = FxPath.position(e, age - echo * 1.1).subtract(here);
            float fa = a * (echo == 0 ? 1f : 0.3f / echo);
            for (int h = 0; h < 4; h++) {
                float ang = (h < 2 ? open : -open) + (h % 2 == 1 ? Mth.PI : 0f);
                draw(ps, vc, blade, pos, dir, Vec3.ZERO, e.scale, c, fa, spin + ang, Mth.HALF_PI);
            }
        }
    }

    // ------------------------------------------------------------------ Krallen-Hieb (Killua, Xeno)

    private void claws(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float u, float[] c, float a) {
        Vec3 dir = safe(e.p1.subtract(e.p0));
        float mirror = e.f0 >= 0f ? 1f : -1f;
        float t = Mth.clamp(u * 1.6f, 0f, 1f);
        float ease = 1f - (1f - t) * (1f - t);
        float sweep = Mth.lerp(ease, -1.0f, 1.0f) * mirror;
        Vec3 base = e.p0.subtract(here);
        for (int k = -1; k <= 1; k++) {
            draw(ps, vc, blade, base, dir, new Vec3(k * 0.35 * e.scale, 0, 0), e.scale * 0.7f, c, a, sweep + k * 0.06f, Mth.HALF_PI);
        }
    }

    // ------------------------------------------------------------------ Blitz

    private void bolt(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float[] c, float a) {
        Vec3 a0 = e.p0.subtract(here);
        Vec3 d = e.p1.subtract(e.p0);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 upv = right.cross(dir).normalize();
        int n = Math.max(4, (int) (len / 1.3));
        RandomSource rnd = RandomSource.create(e.getId() * 31L + (long) (age * 2.5f));
        float[] white = {1f, 1f, 1f};
        Vec3 prev = a0;
        for (int i = 1; i <= n; i++) {
            Vec3 p = a0.add(d.scale(i / (double) n));
            if (i < n) {
                double j = 0.45 * e.scale;
                p = p.add(right.scale((rnd.nextDouble() - 0.5) * 2 * j)).add(upv.scale((rnd.nextDouble() - 0.5) * 2 * j));
            }
            float flicker = 0.7f + 0.3f * rnd.nextFloat();
            bar(ps, vc, prev, p, 0.35f * e.scale, c, a * 0.28f * flicker);
            bar(ps, vc, prev, p, 0.10f * e.scale, white, a * flicker);
            prev = p;
        }
    }

    // ------------------------------------------------------------------ Tornado (Wirbelsturm)

    private void tornado(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float age, float[] c, float a) {
        Vec3 base = e.p0.subtract(here);
        for (int l = 0; l < 8; l++) {
            double y = l * 0.5 * e.scale + 0.1;
            double r = (0.5 + l * 0.28) * e.scale;
            double a0 = age * 0.55 + l * 0.7;
            for (int k = 0; k < 3; k++) {
                double start = a0 + k * Math.PI * 2 / 3;
                Vec3 prev = null;
                for (int s = 0; s <= 5; s++) {
                    double ang = start + s * 0.25;
                    Vec3 p = base.add(Math.cos(ang) * r, y + s * 0.03, Math.sin(ang) * r);
                    if (prev != null) bar(ps, vc, prev, p, 0.14f * e.scale, c, a * (0.5f + 0.1f * (7 - l)));
                    prev = p;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Schockkuppel

    private void dome(FxEntity e, PoseStack ps, VertexConsumer vc, Vec3 here, float u, float[] c, float a) {
        Vec3 base = e.p0.subtract(here);
        float rr = e.scale * (1f - (1f - u) * (1f - u));
        float fa = a * (1f - u);
        int[] elev = {0, 25, 50, 70};
        for (int el : elev) {
            double rad = Math.toRadians(el);
            ring(ps, vc, base, rr * Math.cos(rad), rr * Math.sin(rad) * 0.55 + 0.1, 28, 0.06f * e.scale, c, fa, 0.0);
        }
    }
}
