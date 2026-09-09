package com.polar.polarsensordatacollector.ui.landing

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.PolarDeviceInfo

/**
 * RecyclerView adapter for the sensor selection dialog.
 *
 * Displays two sections:
 *  1. Already-connected devices (sorted alphabetically, shown with a green "CONNECTED" badge)
 *  2. Discovered (not yet connected) devices (sorted by RSSI descending)
 *
 * A separator row with the label "Connected" is injected between the two sections whenever
 * at least one connected device is present in the list.
 */
class SensorListAdapter(
    private val connectedDeviceIds: Set<String>,
    private val onItemSelected: (PolarDeviceInfo?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class SensorListItem {
        data class Device(val info: PolarDeviceInfo, val isConnected: Boolean) : SensorListItem()
        data class Separator(val label: String) : SensorListItem()
    }

    private val items: MutableList<SensorListItem> = mutableListOf()

    /** Rebuild the displayed list from a flat list of scanned [PolarDeviceInfo].
     *  Call whenever new scan results arrive. */
    fun updateDevices(raw: List<PolarDeviceInfo>) {
        val connected = raw.filter { it.deviceId in connectedDeviceIds }
            .sortedBy { it.name }
        val available = raw.filter { it.deviceId !in connectedDeviceIds }
            .sortedByDescending { it.rssi }

        items.clear()
        if (connected.isNotEmpty()) {
            items.add(SensorListItem.Separator("Connected"))
            connected.forEach { items.add(SensorListItem.Device(it, isConnected = true)) }
            items.add(SensorListItem.Separator("Available"))
        }
        available.forEach { items.add(SensorListItem.Device(it, isConnected = false)) }
        notifyDataSetChanged()
    }

    // ── ViewHolder types ──────────────────────────────────────────────────────────────────────

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.device_name)
        val rssi: TextView = view.findViewById(R.id.device_rssi)
        val badge: TextView = view.findViewById(R.id.device_connected_badge)
        val background: LinearLayout = view.findViewById(R.id.device_item_background)

        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    (items[pos] as? SensorListItem.Device)?.let {
                        onItemSelected(it.info)
                    }
                }
            }
        }
    }

    class SeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.separator_label)
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────────────────────

    companion object {
        private const val VIEW_TYPE_DEVICE = 0
        private const val VIEW_TYPE_SEPARATOR = 1
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SensorListItem.Device -> VIEW_TYPE_DEVICE
        is SensorListItem.Separator -> VIEW_TYPE_SEPARATOR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEPARATOR -> SeparatorViewHolder(
                inflater.inflate(R.layout.device_list_separator, parent, false)
            )
            else -> DeviceViewHolder(
                inflater.inflate(R.layout.device_list_item, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SensorListItem.Separator -> {
                (holder as SeparatorViewHolder).label.text = item.label
            }
            is SensorListItem.Device -> {
                holder as DeviceViewHolder
                holder.name.text = item.info.name
                holder.rssi.text = "${item.info.rssi} dbm"

                if (item.isConnected) {
                    holder.badge.visibility = VISIBLE
                    holder.background.setBackgroundColor(
                        holder.itemView.context.getColor(R.color.color_already_connected)
                    )
                } else if (item.info.isConnectable) {
                    holder.badge.visibility = GONE
                    holder.background.setBackgroundColor(
                        holder.itemView.context.getColor(R.color.color_connected)
                    )
                } else {
                    holder.badge.visibility = GONE
                    holder.background.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // Stable IDs: use device ID hash for devices, a fixed sentinel for separators.
    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        is SensorListItem.Device -> item.info.deviceId.hashCode().toLong()
        is SensorListItem.Separator -> item.label.hashCode().toLong() + Long.MIN_VALUE / 2
    }

    // Legacy interface kept for Java interop (DialogUtility is Kotlin so this is for safety).
    fun interface ItemSelected {
        fun itemSelected(info: PolarDeviceInfo?)
    }
}
