package carpet.mixins;

import carpet.helpers.TickSpeed;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.SystemUtil;
import net.minecraft.util.profiler.DisableableProfiler;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServer_tickspeedMixin
{
    @Shadow private volatile boolean running;

    @Shadow private long timeReference;

    @Shadow private long field_4557;

    @Shadow @Final private static Logger LOGGER;

    @Shadow private boolean profilerStartQueued;

    @Shadow @Final private DisableableProfiler profiler;

    @Shadow protected abstract void tick(BooleanSupplier booleanSupplier_1);

    @Shadow protected abstract boolean shouldKeepTicking();

    @Shadow private boolean field_19249;

    @Shadow private long field_19248;

    @Shadow protected abstract void method_16208();

    @Shadow private volatile boolean loading;

    // Cancel a while statement
    @Redirect(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;running:Z"))
    private boolean cancelRunLoop(MinecraftServer server)
    {
        return false;
    }

    // Replaced the above cancelled while statement with this one
    // could possibly just inject that mspt selection at the beginning of the loop, but then adding all mspt's to
    // replace 50L will be a hassle
    @Inject(method = "run", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/MinecraftServer;setFavicon(Lnet/minecraft/server/ServerMetadata;)V"))
    private void modifiedRunLoop(CallbackInfo ci)
    {
        while (this.running)
        {
            //long long_1 = SystemUtil.getMeasuringTimeMs() - this.timeReference;
            //CM deciding on tick speed
            long mspt = 0L;
            long long_1 = 0L;
            if (TickSpeed.time_warp_start_time != 0 && TickSpeed.continueWarp())
            {
                //making sure server won't flop after the warp or if the warp is interrupted
                this.timeReference = this.field_4557 = SystemUtil.getMeasuringTimeMs();
            }
            else
            {
                mspt = TickSpeed.mspt; // regular tick
                long_1 = SystemUtil.getMeasuringTimeMs() - this.timeReference;
            }
            //end tick deciding
            //smoothed out delay to include mcpt component. With 50L gives defaults.
            if (long_1 > /*2000L*/1000L+20*mspt && this.timeReference - this.field_4557 >= /*15000L*/10000L+100*mspt)
            {
                long long_2 = long_1 / mspt;//50L;
                LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", long_1, long_2);
                this.timeReference += long_2 * mspt;//50L;
                this.field_4557 = this.timeReference;
            }

            this.timeReference += mspt;//50L;
            if (this.profilerStartQueued)
            {
                this.profilerStartQueued = false;
                this.profiler.getController().enable();
            }

            this.profiler.startTick();
            this.profiler.push("tick");
            this.tick(this::shouldKeepTicking);
            this.profiler.swap("nextTickWait");
            this.field_19249 = true;
            this.field_19248 = Math.max(SystemUtil.getMeasuringTimeMs() + /*50L*/ mspt, this.timeReference);
            this.method_16208();
            this.profiler.pop();
            this.profiler.endTick();
            this.loading = true;
        }

    }
}
