package com.amazonaws.services.chime.sdkdemo.ui.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.services.chime.sdkdemo.R
import com.amazonaws.services.chime.sdkdemo.data.DoctorItem
import com.amazonaws.services.chime.sdkdemo.inflate
import com.amazonaws.services.chime.sdkdemo.util.ItemClickListener

class PatientAdapter(private val listItem: List<DoctorItem>) : RecyclerView.Adapter<ViewHolder>() {
    private var mItemClickListener: ItemClickListener<DoctorItem> ?= null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflatedView = parent.inflate(R.layout.patient_item, false)
        return ViewHolder(inflatedView)
    }

    override fun getItemCount(): Int = listItem.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val videoCollectionTile = listItem[position]
        holder.bindVideoTile(videoCollectionTile)
        holder.itemView.setOnClickListener {
            mItemClickListener?.onClick(holder.itemView, videoCollectionTile,position = position)
        }
    }

    fun setPatientItemClickListener(listener: ItemClickListener<DoctorItem>) {
        this.mItemClickListener = listener
    }
}


class ViewHolder(inflatedView: View) : RecyclerView.ViewHolder(inflatedView) {

    private var view: View = inflatedView

    fun bindVideoTile(item: DoctorItem) {
        val name = view.findViewById<TextView>(R.id.name)
        val post= view.findViewById<TextView>(R.id.post)
        val section = view.findViewById<TextView>(R.id.section)
        val hospital = view.findViewById<TextView>(R.id.hospital)
        val illness = view.findViewById<TextView>(R.id.illness)
        val description = view.findViewById<TextView>(R.id.description)
        val layout = view.findViewById<LinearLayout>(R.id.item)
        name.text = item.name
        post.text = item.postName
        section.text = item.sectionName
        hospital.text = item.hospitalName
        illness.text = item.illnessName
        description.text = item.description
    }
}

