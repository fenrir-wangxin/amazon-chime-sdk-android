package com.amazonaws.services.chime.sdkdemo.data

import java.io.Serializable

data class DoctorItem (
    var name: String,
    var postName: String,
    var sectionName: String,
    var hospitalName: String,
    var illnessName: String,
    var description: String
): Serializable {
    companion object {
        fun makeDoctorItemList(): List<DoctorItem> {
            val list = mutableListOf<DoctorItem>()
            val item1 = DoctorItem("山崎　佐","主任医師","眼科","大阪警察病院","近視","オンラインサービス患者693人")
            val item2 = DoctorItem("上杉　奈","主任医師","整骨科","東京大学医学部附属病院","腰椎椎間板ヘルニア","オンラインサービス患者893人")
            val item3 = DoctorItem("藤崎　奈菜子","主任医師","整骨科","東京大学医学部附属病院","腰椎椎間板ヘルニア","オンラインサービス患者893人")
            val item4 = DoctorItem("山崎　健","主任医師","整骨科","大阪警察病院","近視","オンラインサービス患者893人")
            val item5 = DoctorItem("山崎　佐","主任医師","眼科","大阪警察病院","近視","オンラインサービス患者693人")
            val item6 = DoctorItem("上杉　奈","主任医師","整骨科","東京大学医学部附属病院","腰椎椎間板ヘルニア","オンラインサービス患者893人")
            val item7 = DoctorItem("山崎　佐","主任医師","眼科","大阪警察病院","近視","オンラインサービス患者693人")
            val item8 = DoctorItem("上杉　奈","主任医師","整骨科","東京大学医学部附属病院","腰椎椎間板ヘルニア","オンラインサービス患者893人")
            list.add(item1)
            list.add(item2)
            list.add(item3)
            list.add(item4)
            list.add(item5)
            list.add(item6)
            list.add(item7)
            list.add(item8)
            return list
        }
    }
}

