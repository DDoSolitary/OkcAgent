package org.ddosolitary.okcagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.openintents.ssh.authentication.util.SshAuthenticationApiUtils

const val EXTRA_PROVIDER_PACKAGE = "org.ddosolitary.okcagent.extra.PROVIDER_PACKAGE"

class SelectProviderActivity : Activity() {
	class ProviderItemAdapter(private val activity: Activity) :
		RecyclerView.Adapter<ProviderItemAdapter.ProviderItemViewHolder>() {
		private val list = SshAuthenticationApiUtils.getAuthenticationProviderInfo(activity)

		class ProviderItemViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

		override fun getItemCount() = list.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderItemViewHolder {
			val view = LayoutInflater.from(parent.context).inflate(
				R.layout.provider_item,
				parent,
				false
			) as TextView
			return ProviderItemViewHolder(view)
		}

		override fun onBindViewHolder(holder: ProviderItemViewHolder, position: Int) {
			val pm = holder.view.context.packageManager
			val item = list[position]
			holder.view.apply {
				text = item.loadLabel(pm)
				setCompoundDrawablesWithIntrinsicBounds(item.loadIcon(pm), null, null, null)
				setOnClickListener {
					activity.apply {
						setResult(RESULT_OK, Intent().apply {
							putExtra(EXTRA_PROVIDER_PACKAGE, item.serviceInfo.packageName)
						})
						finish()
					}
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val activity = this
		setContentView(R.layout.activity_select_provider)
		findViewById<RecyclerView>(R.id.recycler_providers).apply {
			setHasFixedSize(true)
			layoutManager = LinearLayoutManager(activity)
			adapter = ProviderItemAdapter(activity)
		}
	}
}
