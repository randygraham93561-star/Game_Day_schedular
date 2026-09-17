package com.sports.gamedayschedular.ui.org

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sports.gamedayschedular.R
import com.sports.gamedayschedular.data.model.Organization
import com.sports.gamedayschedular.data.model.SubscriptionTier
import com.sports.gamedayschedular.databinding.FragmentOrgSelectionBinding
import kotlinx.coroutines.launch
import java.util.*

class OrgSelectionFragment : Fragment() {

    private var _binding: FragmentOrgSelectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OrgSelectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrgSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OrgAdapter { org ->
            handleOrgSelection(org)
        }
        binding.recyclerOrganizations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerOrganizations.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.organizations.collect { orgs ->
                    adapter.submitList(orgs)
                }
            }
        }
    }

    private fun handleOrgSelection(org: Organization) {
        // Verification Logic
        val isExpired = org.subscriptionExpiresAt?.let { it.before(Date()) } ?: true
        val hasActiveTier = org.subscriptionTier != SubscriptionTier.Free

        if (hasActiveTier && !isExpired) {
            // Save selection
            val sharedPref = requireActivity().getSharedPreferences("GDS_PREFS", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("selected_org_id", org.id)
                putString("selected_org_name", org.name)
                apply()
            }
            findNavController().navigate(R.id.action_nav_org_selection_to_nav_transform)
        } else {
            Toast.makeText(
                requireContext(),
                "Organization subscription is inactive or expired.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class OrgAdapter(private val onClick: (Organization) -> Unit) :
        RecyclerView.Adapter<OrgAdapter.ViewHolder>() {

        private var items: List<Organization> = emptyList()

        fun submitList(newItems: List<Organization>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = item.name
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }
    }
}
