package com.hover.stax.transactions

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.hover.sdk.actions.HoverAction
import com.hover.stax.R
import com.hover.stax.bounties.BountyActivity
import com.hover.stax.contacts.StaxContact
import com.hover.stax.databinding.FragmentTransactionBinding
import com.hover.stax.navigation.NavigationInterface
import com.hover.stax.utils.DateUtils.humanFriendlyDate
import com.hover.stax.utils.UIHelper
import com.hover.stax.utils.Utils.logAnalyticsEvent
import com.hover.stax.utils.Utils.logErrorAndReportToFirebase
import org.json.JSONException
import org.json.JSONObject
import org.koin.androidx.viewmodel.ext.android.viewModel


class TransactionDetailsFragment(private val uuid: String, private val isFullScreen: Boolean) : DialogFragment(), NavigationInterface {

    private val viewModel: TransactionDetailsViewModel by viewModel()
    private var binding: FragmentTransactionBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val data = JSONObject()
        try {
            data.put("uuid", uuid)
        } catch (e: JSONException) {
            logErrorAndReportToFirebase(TransactionDetailsFragment::class.java.simpleName, e.message!!, e)
        }

        logAnalyticsEvent(getString(R.string.visit_screen, getString(R.string.visit_transaction)), data, requireContext())
        binding = FragmentTransactionBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFullScreen) setFullScreen()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!isFullScreen) popUpSize()
    }

    private fun setFullScreen() {
        setStyle(STYLE_NO_FRAME, R.style.StaxDialogFullScreen);
    }

    private fun DialogFragment.popUpSize() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updatePopupDesign()
        startObservers()
        setUssdSessionMessagesRecyclerView()
        viewModel.setTransaction(uuid)
        setupBackButton()
        setupSeeMoreButton()

    }

    private fun setUssdSessionMessagesRecyclerView() {
        setUSSDMessagesRecyclerView()
        if (isFullScreen) setSmsMessagesRecyclerView()
    }

    private fun startObservers() {
        viewModel.transaction.observe(viewLifecycleOwner, { transaction: StaxTransaction? -> showTransaction(transaction) })
        viewModel.action.observe(viewLifecycleOwner, { action: HoverAction? -> showActionDetails(action) })
        viewModel.contact.observe(viewLifecycleOwner, { contact: StaxContact? -> updateRecipient(contact) })
    }

    private fun setupSeeMoreButton() {
        val retryButton = binding!!.retrySubmit.btnRetry
        val bountyButtonsLayout = binding!!.retrySubmit.bountyRetryButtonLayoutId
        if (!isFullScreen) {
            bountyButtonsLayout.visibility = View.VISIBLE
            retryButton.setText(R.string.see_more)
            retryButton.setOnClickListener { recreateFullScreen() }
        }
    }

    private fun setupRetryBountyButton() {
        val bountyButtonsLayout = binding!!.retrySubmit.bountyRetryButtonLayoutId
        val retryButton = binding!!.retrySubmit.btnRetry
        bountyButtonsLayout.visibility = View.VISIBLE
        retryButton.setOnClickListener { v: View -> retryBountyClicked(v) }
    }

    private fun updatePopupDesign() {
        if (!isFullScreen) {
            binding!!.ftMainBg.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            binding!!.transactionDetailsCard.setIcon(R.drawable.ic_close_white)

            binding!!.transactionDetailsCard.makeFlatView()
            binding!!.notificationCard.makeFlatView()
            binding!!.messagesCard.makeFlatView()
        }
    }

    private fun showTransaction(transaction: StaxTransaction?) {
        if (transaction != null) {
            if (transaction.isRecorded) setupRetryBountyButton()
            updateDetails(transaction)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDetails(transaction: StaxTransaction) {
        binding!!.transactionDetailsCard.setTitle(transaction.description)
        binding!!.notificationCard.setStateInfo(transaction.fullStatus)
        binding!!.notificationDetail.text = Html.fromHtml(resources.getString(transaction.fullStatus.getDetail()));

        binding!!.infoCard.detailsRecipientLabel.setText(if (transaction.transaction_type == HoverAction.RECEIVE) R.string.sender_label else R.string.recipient_label)
        binding!!.infoCard.detailsAmount.text = transaction.displayAmount
        binding!!.infoCard.detailsDate.text = humanFriendlyDate(transaction.initiated_at)

        if (!transaction.confirm_code.isNullOrEmpty()) binding!!.infoCard.detailsTransactionNumber.text = transaction.confirm_code else binding!!.infoCard.detailsTransactionNumber.text = transaction.uuid
        if (transaction.isRecorded) hideNonBountyDetails()
    }

    private fun hideNonBountyDetails() {
        binding!!.infoCard.amountRow.visibility = View.GONE
        binding!!.infoCard.recipientRow.visibility = View.GONE
        binding!!.infoCard.recipAccountRow.visibility = View.GONE
    }

    private fun showActionDetails(action: HoverAction?) {
        binding!!.infoCard.detailsNetwork.text = action?.from_institution_name
    }

    private fun updateRecipient(contact: StaxContact?) {
        if (contact != null) binding!!.infoCard.detailsRecipient.setContact(contact)
    }

    private fun setUSSDMessagesRecyclerView() {
        val messagesView = binding!!.convoRecyclerView
        messagesView.layoutManager = UIHelper.setMainLinearManagers(requireActivity())
        messagesView.setHasFixedSize(true)
        viewModel.messages.observe(viewLifecycleOwner, { if (it != null) messagesView.adapter = MessagesAdapter(it, if (isFullScreen) 0 else 1) })
    }

    private fun setSmsMessagesRecyclerView() {
        val smsView = binding!!.smsRecyclerView
        smsView.layoutManager = UIHelper.setMainLinearManagers(requireActivity())
        smsView.setHasFixedSize(true)
        viewModel.sms.observe(viewLifecycleOwner, { if (it != null) smsView.adapter = MessagesAdapter(it) })
    }

    private fun setupBackButton() {
        binding!!.transactionDetailsCard.setOnClickIcon { this.dismiss() }
    }

    private fun recreateFullScreen() {
        this.dismiss()
        val frag = TransactionDetailsFragment(uuid, true)
        frag.show(parentFragmentManager, "dialogFrag")
    }

    private fun retryBountyClicked(v: View) {
        viewModel.transaction.value?.let {
            (requireActivity() as BountyActivity).retryCall(it.action_id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}