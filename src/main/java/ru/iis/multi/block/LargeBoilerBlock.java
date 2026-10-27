package ru.iis.multi.block;

import ru.iis.multi.logic.LargeBoilerLogic;

/** Контроллер большого котла (D88): уровень котла задаёт мощность, прирост КПД и нужные корпуса. */
public class LargeBoilerBlock extends EbfBlock {
    public final LargeBoilerLogic.Tier tier;

    public LargeBoilerBlock(Properties properties, LargeBoilerLogic.Tier tier) {
        super(properties, LargeBoilerBlockEntity::new);
        this.tier = tier;
    }
}
