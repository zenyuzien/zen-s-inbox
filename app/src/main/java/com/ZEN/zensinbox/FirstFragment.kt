package com.ZEN.zensinbox

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
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

        try {
            setupRecyclerView()
            loadConversations()
        } catch (e: Exception) {
            Log.e("FirstFragment", "Error in onViewCreated", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            try {
                findNavController().navigate(
                    R.id.action_FirstFragment_to_SecondFragment,
                    bundleOf(
                        "threadId" to conversation.threadId,
                        "address" to conversation.address
                    )
                )
            } catch (e: Exception) {
                Log.e("FirstFragment", "Navigation error", e)
                Toast.makeText(context, "Navigation error", Toast.LENGTH_SHORT).show()
            }
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
            Toast.makeText(context, "SMS permission required. Please grant permission.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val conversations = mutableListOf<Conversation>()
            val conversationMap = mutableMapOf<String, Conversation>()
            
            // Query all SMS messages and group by thread_id
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            val cursor: Cursor? = requireContext().contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                Log.d("FirstFragment", "Cursor count: ${it.count}")
                
                while (it.moveToNext()) {
                    try {
                        val threadId = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                        val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                        val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                        val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                        // Group by thread_id - only keep the most recent message
                        if (!conversationMap.containsKey(threadId)) {
                            conversationMap[threadId] = Conversation(
                                threadId = threadId,
                                address = address,
                                snippet = body,
                                date = date,
                                messageCount = 1
                            )
                        } else {
                            // Increment message count
                            val existing = conversationMap[threadId]!!
                            conversationMap[threadId] = existing.copy(
                                messageCount = existing.messageCount + 1
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("FirstFragment", "Error parsing message", e)
                    }
                }
            }

            // Convert map to sorted list
            conversations.addAll(conversationMap.values.sortedByDescending { it.date })

            Log.d("FirstFragment", "Loaded ${conversations.size} conversations")
            
            if (conversations.isEmpty()) {
                Toast.makeText(context, "No messages found", Toast.LENGTH_SHORT).show()
            }
            
            adapter.submitList(conversations)
            
        } catch (e: Exception) {
            Log.e("FirstFragment", "Error loading conversations", e)
            Toast.makeText(context, "Error loading messages: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
            textViewAddress.text = conversation.address
            textViewSnippet.text = if (conversation.snippet.length > 50) {
                conversation.snippet.substring(0, 50) + "..."
            } else {
                conversation.snippet
            }
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
        return try {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown"
        }
    }
}