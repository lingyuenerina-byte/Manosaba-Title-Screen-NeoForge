package me.shiiyuko.manosaba.bridge;

import java.util.List;
import net.minecraft.server.packs.repository.Pack;

/**
 * 数据包选择模型（PackSelectionModel）的宿主能力接口：暴露内部两份 Pack 列表，
 * 供内嵌的 Compose 界面直接按 Pack 读取图标 / 标题 / 描述（Entry 接口不暴露 Pack）。
 *
 * <p>返回的是模型内部列表引用，调用方应按需拷贝（.toList()）后再用于状态展示。
 */
public interface PackSelectionModelHost {

    /** 已选数据包列表（与模型内部列表同序）。 */
    List<Pack> manosaba$selectedPacks();

    /** 未选数据包列表（与模型内部列表同序）。 */
    List<Pack> manosaba$unselectedPacks();
}
