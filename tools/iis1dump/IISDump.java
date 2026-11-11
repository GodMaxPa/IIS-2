package iisdump;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/** One-shot dump of the recipes of the running 1.7.10 pack (IIS 1) into text files, then the server stops. Reflection only. */
@Mod(modid = "iisdump", name = "IIS Dump", version = "1.0", acceptableRemoteVersions = "*", dependencies = "after:*")
public class IISDump {
    int ticks = -1;
    final Map<String, String> names = new TreeMap<String, String>();
    final IdentityHashMap<Object, String> oreLists = new IdentityHashMap<Object, String>();
    Class<?> stackClass;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent e) {
        ticks = 0;
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (ticks < 0 || e.phase != TickEvent.Phase.END) return;
        if (++ticks != 100) return;
        File dir = new File("iis1_dump");
        dir.mkdirs();
        try {
            stackClass = Class.forName("net.minecraft.item.ItemStack");
            section(dir, "oredict", new Job() { public void run(PrintWriter w) throws Exception { oredict(w); } });
            section(dir, "crafting", new Job() { public void run(PrintWriter w) throws Exception { crafting(w); } });
            section(dir, "furnace", new Job() { public void run(PrintWriter w) throws Exception { furnace(w); } });
            section(dir, "gt", new Job() { public void run(PrintWriter w) throws Exception { gt(w); } });
            section(dir, "names", new Job() { public void run(PrintWriter w) { for (Map.Entry<String, String> n : names.entrySet()) w.println(n.getKey() + "\t" + n.getValue()); } });
            new File(dir, "DONE").createNewFile();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            Object server = FMLCommonHandler.class.getMethod("getMinecraftServerInstance").invoke(FMLCommonHandler.instance());
            server.getClass().getMethod("func_71263_m").invoke(server);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(0);
        }
    }

    interface Job { void run(PrintWriter w) throws Exception; }

    void section(File dir, String name, Job job) {
        try {
            PrintWriter w = new PrintWriter(new File(dir, name + ".txt"), "UTF-8");
            try { job.run(w); } catch (Throwable t) { w.println("!!! " + t); t.printStackTrace(w); t.printStackTrace(); }
            w.close();
            System.out.println("[iisdump] wrote " + name);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    static Object call(Object o, String m) throws Exception {
        Method x = o.getClass().getMethod(m);
        x.setAccessible(true);
        return x.invoke(o);
    }

    static Object field(Object o, Class<?> c, String f) throws Exception {
        Field x = c.getDeclaredField(f);
        x.setAccessible(true);
        return x.get(o);
    }

    String stack(Object s) {
        if (s == null) return "null";
        try {
            Object item = call(s, "func_77973_b");
            if (item == null) return "noitem";
            Object uid = Class.forName("cpw.mods.fml.common.registry.GameRegistry").getMethod("findUniqueIdentifierFor", Class.forName("net.minecraft.item.Item")).invoke(null, item);
            int meta = (Integer) call(s, "func_77960_j");
            int n = stackClass.getField("field_77994_a").getInt(s);
            String key = uid + "@" + meta;
            if (!names.containsKey(key)) {
                String dn;
                try { dn = meta == 32767 ? "*" : String.valueOf(call(s, "func_82833_r")); } catch (Throwable t) { dn = "?"; }
                String un;
                try { un = meta == 32767 ? "*" : String.valueOf(call(s, "func_77977_a")); } catch (Throwable t) { un = "?"; }
                names.put(key, dn.replace('\t', ' ').replace('\n', ' ') + "\t" + un);
            }
            Object tag = stackClass.getField("field_77990_d").get(s);
            return key + (n != 1 ? "*" + n : "") + (tag != null ? "{" + String.valueOf(tag).replace('\t', ' ').replace('\n', ' ') + "}" : "");
        } catch (Throwable t) {
            return "ERR(" + t + ")";
        }
    }

    /** Recipe ingredient: stack, ore list (by identity), list of stacks, string, IC2 IRecipeInput, null. */
    String ing(Object o, int depth) {
        if (o == null) return "null";
        if (stackClass.isInstance(o)) return stack(o);
        if (o instanceof String) return "ore:" + o;
        if (oreLists.containsKey(o)) return "ore:" + oreLists.get(o);
        if (o instanceof Collection) {
            StringBuilder b = new StringBuilder("[");
            for (Object x : (Collection<?>) o) { if (b.length() > 1) b.append('|'); b.append(ing(x, depth + 1)); }
            return b.append(']').toString();
        }
        if (o.getClass().isArray()) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < Array.getLength(o); i++) { if (i > 0) b.append('|'); b.append(ing(Array.get(o, i), depth + 1)); }
            return b.append(']').toString();
        }
        if (o instanceof Number || o instanceof Boolean || o instanceof Character) return String.valueOf(o);
        if (depth < 3) {
            try {
                Method m = o.getClass().getMethod("getInputs");
                m.setAccessible(true);
                String amount = "";
                try { Method a = o.getClass().getMethod("getAmount"); a.setAccessible(true); amount = "*" + a.invoke(o); } catch (Throwable ignored) {}
                return o.getClass().getSimpleName() + ing(m.invoke(o), depth + 1) + amount;
            } catch (Throwable ignored) {}
        }
        return "obj:" + o.getClass().getName();
    }

    void oredict(PrintWriter w) throws Exception {
        Class<?> od = Class.forName("net.minecraftforge.oredict.OreDictionary");
        String[] all = (String[]) od.getMethod("getOreNames").invoke(null);
        Arrays.sort(all);
        for (String n : all) {
            Object list = od.getMethod("getOres", String.class).invoke(null, n);
            oreLists.put(list, n);
            StringBuilder b = new StringBuilder(n);
            for (Object s : (Collection<?>) list) b.append('\t').append(stack(s));
            w.println(b);
        }
    }

    void crafting(PrintWriter w) throws Exception {
        Class<?> cm = Class.forName("net.minecraft.item.crafting.CraftingManager");
        Object inst = cm.getMethod("func_77594_a").invoke(null);
        List<?> list = (List<?>) cm.getMethod("func_77592_b").invoke(inst);
        Class<?> shaped = Class.forName("net.minecraft.item.crafting.ShapedRecipes");
        Class<?> shapeless = Class.forName("net.minecraft.item.crafting.ShapelessRecipes");
        Class<?> oreShaped = Class.forName("net.minecraftforge.oredict.ShapedOreRecipe");
        Class<?> oreShapeless = Class.forName("net.minecraftforge.oredict.ShapelessOreRecipe");
        for (Object r : list) {
            String out;
            try { out = stack(call(r, "func_77571_b")); } catch (Throwable t) { out = "ERR"; }
            String cls = r.getClass().getName();
            try {
                if (oreShaped.isInstance(r)) {
                    w.println("shaped\t" + cls + "\t" + out + "\t" + field(r, oreShaped, "width") + "x" + field(r, oreShaped, "height") + "\t" + ing(call(r, "getInput"), 0));
                } else if (oreShapeless.isInstance(r)) {
                    w.println("shapeless\t" + cls + "\t" + out + "\t\t" + ing(call(r, "getInput"), 0));
                } else if (shaped.isInstance(r)) {
                    w.println("shaped\t" + cls + "\t" + out + "\t" + field(r, shaped, "field_77576_b") + "x" + field(r, shaped, "field_77577_c") + "\t" + ing(field(r, shaped, "field_77574_d"), 0));
                } else if (shapeless.isInstance(r)) {
                    w.println("shapeless\t" + cls + "\t" + out + "\t\t" + ing(field(r, shapeless, "field_77579_b"), 0));
                } else {
                    StringBuilder b = new StringBuilder("other\t" + cls + "\t" + out + "\t\t");
                    for (Class<?> c = r.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (Modifier.isStatic(f.getModifiers())) continue;
                            f.setAccessible(true);
                            b.append(f.getName()).append('=').append(ing(f.get(r), 0)).append(';');
                        }
                    }
                    w.println(b);
                }
            } catch (Throwable t) {
                w.println("error\t" + cls + "\t" + out + "\t\t" + t);
            }
        }
    }

    void furnace(PrintWriter w) throws Exception {
        Class<?> fr = Class.forName("net.minecraft.item.crafting.FurnaceRecipes");
        Object inst = fr.getMethod("func_77602_a").invoke(null);
        Map<?, ?> map = (Map<?, ?>) fr.getMethod("func_77599_b").invoke(inst);
        for (Map.Entry<?, ?> e : map.entrySet()) w.println(stack(e.getKey()) + "\t" + stack(e.getValue()));
    }

    String fluids(Object arr) throws Exception {
        if (arr == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Array.getLength(arr); i++) {
            Object f = Array.get(arr, i);
            if (b.length() > 0) b.append('|');
            if (f == null) { b.append("null"); continue; }
            Object fluid = call(f, "getFluid");
            b.append(fluid == null ? "?" : call(fluid, "getName")).append('*').append(f.getClass().getField("amount").getInt(f));
        }
        return b.toString();
    }

    String stacks(Object arr) {
        if (arr == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Array.getLength(arr); i++) { if (i > 0) b.append('|'); b.append(stack(Array.get(arr, i))); }
        return b.toString();
    }

    void gt(PrintWriter w) throws Exception {
        Class<?> mapClass = Class.forName("gregtech.api.util.GT_Recipe$GT_Recipe_Map");
        Class<?> c = Class.forName("gregtech.api.util.GT_Recipe");
        Collection<?> maps = (Collection<?>) mapClass.getField("sMappings").get(null);
        for (Object m : maps) {
            Collection<?> list = (Collection<?>) mapClass.getField("mRecipeList").get(m);
            String name = String.valueOf(mapClass.getField("mUnlocalizedName").get(m));
            w.println("#map\t" + name + "\t" + mapClass.getField("mNEIName").get(m) + "\t" + list.size());
            for (Object r : list) {
                int[] ch = (int[]) c.getField("mChances").get(r);
                Object special = c.getField("mSpecialItems").get(r);
                w.println(name + "\t" + stacks(c.getField("mInputs").get(r)) + "\t" + fluids(c.getField("mFluidInputs").get(r)) + "\t" + stacks(c.getField("mOutputs").get(r))
                        + "\t" + fluids(c.getField("mFluidOutputs").get(r)) + "\t" + c.getField("mDuration").getInt(r) + "\t" + c.getField("mEUt").getInt(r)
                        + "\t" + c.getField("mSpecialValue").getInt(r) + "\t" + (ch == null ? "" : Arrays.toString(ch)) + "\t" + (special == null ? "" : ing(special, 0))
                        + "\t" + (c.getField("mEnabled").getBoolean(r) ? "" : "disabled") + (c.getField("mHidden").getBoolean(r) ? " hidden" : "") + (c.getField("mFakeRecipe").getBoolean(r) ? " fake" : ""));
            }
        }
        try {
            Class<?> al = Class.forName("gregtech.api.util.GT_Recipe$GT_Recipe_AssemblyLine");
            Collection<?> list = (Collection<?>) al.getField("sAssemblylineRecipes").get(null);
            w.println("#map\tassemblyline\tassemblyline\t" + list.size());
            for (Object r : list) {
                w.println("assemblyline\t" + stacks(al.getField("mInputs").get(r)) + "\t" + fluids(al.getField("mFluidInputs").get(r)) + "\t" + stack(al.getField("mOutput").get(r))
                        + "\t\t" + al.getField("mDuration").getInt(r) + "\t" + al.getField("mEUt").getInt(r) + "\t" + al.getField("mResearchTime").getInt(r) + "\t\t" + stack(al.getField("mResearchItem").get(r)) + "\t");
            }
        } catch (Throwable t) { w.println("!!! assemblyline " + t); }
    }
}
