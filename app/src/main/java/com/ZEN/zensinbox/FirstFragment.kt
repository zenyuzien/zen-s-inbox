package com.ZEN.zensinbox

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ZEN.zensinbox.databinding.FragmentFirstBinding
import com.ZEN.zensinbox.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.*

data class Conversation(
    val threadId: String,
    val address: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int
)

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadConversations()
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            findNavController().navigate(
                R.id.action_FirstFragment_to_SecondFragment,
                bundleOf(
                    "threadId" to conversation.threadId,
                    "address" to conversation.address
                )
            )
        }
        
        binding.recyclerViewConversations.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FirstFragment.adapter
        }
    }

    private fun loadConversations() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            binding.textViewEmpty.visibility = View.VISIBLE
            binding.textViewEmpty.text = "SMS permission required"
            return
        }

        try {
            val conversations = mutableListOf<Conversation>()
            val conversationMap = mutableMapOf<String, Conversation>()
            
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            val cursor: Cursor? = requireContext().contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val threadId = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                    val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    if (!conversationMap.containsKey(threadId)) {
                        conversationMap[threadId] = Conversation(
                            threadId = threadId,
                            address = address,
                            snippet = body,
                            date = date,
                            messageCount = 1
                        )
                    } else {
                        val existing = conversationMap[threadId]!!
                        conversationMap[threadId] = existing.copy(
                            messageCount = existing.messageCount + 1
                        )
                    }
                }
            }

            conversations.addAll(conversationMap.values.sortedByDescending { it.date })
            
            if (conversations.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
            } else {
                binding.textViewEmpty.visibility = View.GONE
            }
            
            adapter.submitList(conversations)
            
        } catch (e: Exception) {
            Log.e("FirstFragment", "Error loading conversations", e)
            Toast.makeText(context, "Error loading messages", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = "Messages"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ConversationAdapter(
    private val onConversationClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    private var conversations = listOf<Conversation>()

    class ViewHolder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.binding.apply {
            // Set avatar initial
            textViewAvatar.text = conversation.address.firstOrNull()?.toString()?.uppercase() ?: "?"
            
            textViewAddress.text = conversation.address
            textViewSnippet.text = conversation.snippet
            textViewDate.text = formatDate(conversation.date)
            root.setOnClickListener { onConversationClick(conversation) }
        }
    }

    override fun getItemCount() = conversations.size

    fun submitList(newConversations: List<Conversation>) {
        conversations = newConversations
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        return when {
            now.get(Calendar.DATE) == messageTime.get(Calendar.DATE) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR) -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
            }
            else -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}