package ru.iis.rail.cart;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ru.iis.lv.LvContent;
import ru.iis.rail.ElectricRail;
import ru.iis.rail.RailContent;

/**
 * Электровоз Railcraft ({@code EntityLocomotiveElectric}, D102): запас 5 000 EU, 20 EU на единицу хода, холостого хода
 * нет (переключение на холостой ход глушит), заряжается на электрическом пути от фидера сети путей
 * ({@link ElectricRail}).
 */
public class ElectricLocomotive extends Locomotive {
    public static final int MAX_CHARGE = 5000, CHARGE_PER_REQUEST = 20;

    long charge;

    public ElectricLocomotive(EntityType<? extends ElectricLocomotive> type, Level level) {
        super(type, level);
    }

    public long charge() {
        return charge;
    }

    public void addCharge(long eu) {
        charge = Math.min(MAX_CHARGE, charge + eu);
    }

    @Override
    public void setMode(Mode m) {
        super.setMode(m == Mode.IDLE ? Mode.SHUTDOWN : m);
    }

    @Override
    protected int idleFuelUse() {
        return 0;
    }

    @Override
    protected int moreGoJuice() {
        if (charge > CHARGE_PER_REQUEST) {
            charge -= CHARGE_PER_REQUEST;
            return 1;
        }
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && charge < MAX_CHARGE) {
            charge += ElectricRail.draw(sl, this, MAX_CHARGE - charge);
        }
    }

    @Override
    protected Item getDropItem() {
        return RailContent.LOCO_ELECTRIC.get();
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return LvContent.BATTERY_BUFFERS_MV.get(1).get().defaultBlockState();
    }

    @Override
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> mode().ordinal();
                    case 1 -> speed().ordinal();
                    case 6 -> (int) charge;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 7;
            }
        };
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putLong("charge", charge);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        charge = in.getLongOr("charge", 0);
    }
}
