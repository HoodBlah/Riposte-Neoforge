package nel.riposte.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedList;

public class ParryTrailParticle extends TextureSheetParticle {

    private final LinkedList<Vec3> trailHistory = new LinkedList<>();
    private final int maxTrailLength = 40;
    private final float baseSize = 0.035f;
    private final double dotSpacing = 0.22;
    private final boolean isHeavy;

    protected ParryTrailParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, boolean isHeavy) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.isHeavy = isHeavy;

        this.lifetime = 25 + this.random.nextInt(10);
        this.hasPhysics = true;
        this.trailHistory.add(new Vec3(x, y, z));
    }

    @Override
    public void tick() {
        double oldVx = this.xd;
        double oldVy = this.yd;
        double oldVz = this.zd;

        this.yd -= 0.025;
        this.xd *= 0.95;
        this.zd *= 0.95;
        this.yd *= 0.95;

        super.move(this.xd, this.yd, this.zd);

        if (this.xd == 0.0D && oldVx != 0.0D) this.xd = -oldVx * 0.8;
        if (this.onGround || (this.yd == 0.0D && oldVy != 0.0D)) this.yd = -oldVy * 0.8;
        if (this.zd == 0.0D && oldVz != 0.0D) this.zd = -oldVz * 0.8;

        this.trailHistory.addFirst(new Vec3(this.x, this.y, this.z));
        if (this.trailHistory.size() > maxTrailLength) {
            this.trailHistory.removeLast();
        }

        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float partialTicks) {
        if (this.trailHistory.isEmpty() || this.sprite == null) return;

        Vec3 camPos = camera.getPosition();
        float minU = this.sprite.getU0();
        float maxU = this.sprite.getU1();
        float minV = this.sprite.getV0();
        float maxV = this.sprite.getV1();
        int light = 15728880;

        Quaternionf quaternion = camera.rotation();

        float lifeRatio = (float) this.age / this.lifetime;
        float ageFade = lifeRatio > 0.5f ? (1.0f - lifeRatio) * 2.0f : 1.0f;
        if (ageFade < 0) ageFade = 0;

        int count = this.trailHistory.size();
        int segments = Math.max(1, count - 1);

        for (int i = 0; i < segments; i++) {
            Vec3 current = this.trailHistory.get(i);
            Vec3 next = (i + 1 < count) ? this.trailHistory.get(i + 1) : current;

            double dist = current.distanceTo(next);
            int steps = dist > 0 ? Math.max(1, (int) Math.ceil(dist / dotSpacing)) : 1;

            for (int step = 0; step < steps; step++) {
                float stepFraction = (float) step / steps;

                double ix = Mth.lerp(stepFraction, current.x, next.x);
                double iy = Mth.lerp(stepFraction, current.y, next.y);
                double iz = Mth.lerp(stepFraction, current.z, next.z);

                float baseProgress = (float) i / segments;
                float nextProgress = (float) (i + 1) / segments;
                float progress = Mth.lerp(stepFraction, baseProgress, nextProgress);

                float currentSize = this.baseSize * (1.0f - (progress * 0.6f));

                float r, g, b;
                if (this.isHeavy) {
                    if (progress < 0.1f) {
                        r = 1.0f; g = 1.0f; b = 1.0f;
                    } else if (progress < 0.3f) {
                        float p = (progress - 0.1f) / 0.2f;
                        r = Mth.lerp(p, 1.0f, 0.4f);
                        g = Mth.lerp(p, 1.0f, 0.9f);
                        b = 1.0f;
                    } else {
                        float p = (progress - 0.3f) / 0.7f;
                        r = Mth.lerp(p, 0.4f, 0.1f);
                        g = Mth.lerp(p, 0.9f, 0.5f);
                        b = Mth.lerp(p, 1.0f, 0.8f);
                    }
                } else {
                    if (progress < 0.1f) {
                        r = 1.0f; g = 1.0f; b = 1.0f;
                    } else if (progress < 0.3f) {
                        float p = (progress - 0.1f) / 0.2f;
                        r = 1.0f;
                        g = Mth.lerp(p, 1.0f, 0.7f);
                        b = Mth.lerp(p, 1.0f, 0.1f);
                    } else {
                        float p = (progress - 0.3f) / 0.7f;
                        r = Mth.lerp(p, 1.0f, 0.8f);
                        g = Mth.lerp(p, 0.7f, 0.2f);
                        b = Mth.lerp(p, 0.1f, 0.0f);
                    }
                }

                float alpha = ageFade * (1.0f - progress);
                if (alpha <= 0.01f) continue;

                Vector3f[] corners = new Vector3f[]{
                        new Vector3f(-1.0F, -1.0F, 0.0F),
                        new Vector3f(-1.0F,  1.0F, 0.0F),
                        new Vector3f( 1.0F,  1.0F, 0.0F),
                        new Vector3f( 1.0F, -1.0F, 0.0F)
                };

                float cx = (float) (ix - camPos.x);
                float cy = (float) (iy - camPos.y);
                float cz = (float) (iz - camPos.z);

                for (int j = 0; j < 4; ++j) {
                    corners[j].rotate(quaternion);
                    corners[j].mul(currentSize);
                    corners[j].add(cx, cy, cz);
                }

                vertexConsumer.addVertex(corners[0].x(), corners[0].y(), corners[0].z()).setUv(maxU, maxV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[1].x(), corners[1].y(), corners[1].z()).setUv(maxU, minV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[2].x(), corners[2].y(), corners[2].z()).setUv(minU, minV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[3].x(), corners[3].y(), corners[3].z()).setUv(minU, maxV).setColor(r, g, b, alpha).setLight(light);

                vertexConsumer.addVertex(corners[3].x(), corners[3].y(), corners[3].z()).setUv(minU, maxV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[2].x(), corners[2].y(), corners[2].z()).setUv(minU, minV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[1].x(), corners[1].y(), corners[1].z()).setUv(maxU, minV).setColor(r, g, b, alpha).setLight(light);
                vertexConsumer.addVertex(corners[0].x(), corners[0].y(), corners[0].z()).setUv(maxU, maxV).setColor(r, g, b, alpha).setLight(light);
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class HeavyFactory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;
        public HeavyFactory(SpriteSet spriteSet) { this.spriteSet = spriteSet; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            ParryTrailParticle particle = new ParryTrailParticle(level, x, y, z, vx, vy, vz, true);
            particle.pickSprite(this.spriteSet);
            return particle;
        }
    }

    public static class LightFactory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;
        public LightFactory(SpriteSet spriteSet) { this.spriteSet = spriteSet; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            ParryTrailParticle particle = new ParryTrailParticle(level, x, y, z, vx, vy, vz, false);
            particle.pickSprite(this.spriteSet);
            return particle;
        }
    }
}
