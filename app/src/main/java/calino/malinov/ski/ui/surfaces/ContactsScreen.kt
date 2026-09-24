package calino.malinov.ski.ui.surfaces

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.model.ContactEmail
import calino.malinov.ski.data.model.ContactPhone
import calino.malinov.ski.data.model.ContactPhoneType
import calino.malinov.ski.data.model.ContactType
import calino.malinov.ski.data.model.ContactUrl
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.contactAge
import calino.malinov.ski.data.model.daysUntilNextContactDate
import calino.malinov.ski.data.model.derivedDisplayName
import calino.malinov.ski.data.model.hasContactEvent
import calino.malinov.ski.data.model.alphaKey
import calino.malinov.ski.data.model.groupContactsByAlpha
import calino.malinov.ski.data.model.primaryContactEmail
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoShapes
import calino.malinov.ski.design.CalinoSpacing
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.CalinoSurfaceKind
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.state.LocalFoldPosture
import calino.malinov.ski.state.calinoLayoutSpec
import calino.malinov.ski.state.searchContacts
import calino.malinov.ski.ui.components.CalinoChip
import calino.malinov.ski.ui.components.CalinoIcon
import calino.malinov.ski.ui.components.CalinoIcons
import calino.malinov.ski.ui.components.CalinoSearchField
import calino.malinov.ski.ui.components.CalinoTextField
import calino.malinov.ski.ui.components.DetailCardSurface
import calino.malinov.ski.ui.components.DetailRow
import calino.malinov.ski.ui.components.MenuButton
import calino.malinov.ski.ui.components.BottomDetailCard
import calino.malinov.ski.ui.components.ModalActionPill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val ContactDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** Adaptive contact directory. Selection is hoisted so rotation never loses it. */
@Composable
fun ContactsSurface(
    contacts: List<Contact>,
    addressBooks: List<ContactAddressBook>,
    events: List<CalEvent> = emptyList(),
    selectedContactId: String? = null,
    onSelectedContactChanged: (String?) -> Unit = {},
    onCreate: (NewContact) -> Unit = {},
    onUpdate: (Contact) -> Unit = {},
    onDelete: (Contact) -> Unit = {},
    onAddBirthday: (Contact, LocalDate, Boolean) -> Unit = { _, _, _ -> },
    onOpenMenu: (() -> Unit)? = null,
    startEntryRequest: Int = 0,
) {
    val now = LocalCalinoNow.current
    val posture = calino.malinov.ski.state.LocalFoldPosture.current
    var query by rememberSaveable { mutableStateOf("") }
    var bookFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftSequence by rememberSaveable { mutableIntStateOf(0) }
    val selected = contacts.firstOrNull { it.id == selectedContactId }
    val filtered = remember(contacts, query, bookFilter, tagFilter) {
        searchContacts(contacts, query, bookFilter, tagFilter)
    }
    val tags = remember(contacts) { contacts.flatMap { it.categories }.distinct().sorted() }

    fun openNew() {
        draftSequence += 1
        editingId = "draft-contact-$draftSequence"
    }

    LaunchedEffect(startEntryRequest) {
        if (startEntryRequest > 0) openNew()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(CalinoColors.Canvas)) {
        val spec = calinoLayoutSpec(maxWidth.value.toInt(), maxHeight.value.toInt(), posture)
        val split = spec.splitPanes
        val listWidth = spec.hingeStartDp?.dp?.let { minOf(ContactsListPaneWidth, it) } ?: ContactsListPaneWidth
        if (split) {
            Row(Modifier.fillMaxSize()) {
                ContactDirectory(
                    contacts = filtered,
                    addressBooks = addressBooks,
                    bookFilter = bookFilter,
                    tagFilter = tagFilter,
                    tags = tags,
                    query = query,
                    onQueryChanged = { query = it },
                    onBookFilterChanged = { bookFilter = it },
                    onTagFilterChanged = { tagFilter = it },
                    onContact = { onSelectedContactChanged(it.id) },
                    selectedId = selectedContactId,
                    onOpenMenu = onOpenMenu,
                    modifier = Modifier.width(listWidth),
                )
                if (spec.hingeBandDp > 0f) {
                    Spacer(Modifier.width(spec.hingeBandDp.dp).background(CalinoColors.Canvas))
                } else {
                    Box(Modifier.width(1.dp).fillMaxSize().background(CalinoColors.Line))
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(170))) togetherWith
                                (slideOutHorizontally(tween(170)) { -it / 5 } + fadeOut(tween(120)))
                        },
                        label = "contact pane selection",
                    ) { contact ->
                        if (contact == null) ContactPaneEmpty()
                        else DetailCardSurface(Modifier.fillMaxSize()) { contentModifier ->
                            ContactDetailPane(
                                contact = contact,
                                events = events,
                                today = now.today,
                                onBack = { onSelectedContactChanged(null) },
                                onEdit = { editingId = contact.id },
                                onDelete = { onDelete(contact); onSelectedContactChanged(null) },
                                onTag = { tagFilter = it },
                                onAddDate = { date, anniversary -> onAddBirthday(contact, date, anniversary) },
                                modifier = contentModifier,
                            )
                        }
                    }
                }
            }
        } else {
            ContactDirectory(
                contacts = filtered,
                addressBooks = addressBooks,
                bookFilter = bookFilter,
                tagFilter = tagFilter,
                tags = tags,
                query = query,
                onQueryChanged = { query = it },
                onBookFilterChanged = { bookFilter = it },
                onTagFilterChanged = { tagFilter = it },
                onContact = { onSelectedContactChanged(it.id) },
                selectedId = selectedContactId,
                onOpenMenu = onOpenMenu,
                modifier = Modifier.fillMaxSize(),
            )
            // Only while the editor is closed. The editor is opened from this
            // card, and the card is asked to animate out first -- but the
            // selection it is keyed to stays, so leaving it composed would
            // strand a departed sheet on top of the list, its full-screen
            // scrim swallowing every tap while showing nothing at all.
            if (selected != null && editingId == null) {
                // The card used to vanish outright, which took its pill with
                // it. It animates out instead, which is the window the pill
                // needs to morph back into the root add shape.
                var detailShown by remember(selected.id) { mutableStateOf(true) }
                var detailCloseAction by remember(selected.id) { mutableStateOf<(() -> Unit)?>(null) }
                fun closeDetail(action: () -> Unit) {
                    if (detailShown) { detailCloseAction = action; detailShown = false }
                }
                LaunchedEffect(detailShown) {
                    if (!detailShown) {
                        kotlinx.coroutines.delay(220)
                        detailCloseAction?.invoke()
                    }
                }
                BottomDetailCard(
                    visible = detailShown,
                    onDismiss = { closeDetail { onSelectedContactChanged(null) } },
                    surfaceKind = CalinoSurfaceKind.Detail,
                    pill = {
                        ContactDetailPill(
                            expanded = detailShown,
                            inPillLane = true,
                            onBack = { closeDetail { onSelectedContactChanged(null) } },
                            onEdit = { closeDetail { editingId = selected.id } },
                            onDelete = { closeDetail { onDelete(selected); onSelectedContactChanged(null) } },
                        )
                    },
                    content = { modifier ->
                        ContactDetailPane(
                            contact = selected,
                            events = events,
                            today = now.today,
                            onBack = { closeDetail { onSelectedContactChanged(null) } },
                            onEdit = { closeDetail { editingId = selected.id } },
                            onDelete = { closeDetail { onDelete(selected); onSelectedContactChanged(null) } },
                            onTag = { tag -> closeDetail { tagFilter = tag; onSelectedContactChanged(null) } },
                            onAddDate = { date, anniversary -> onAddBirthday(selected, date, anniversary) },
                            modifier = modifier,
                            pillInLane = true,
                        )
                    },
                )
            }
        }
    }

    val editing = editingId?.let { id -> contacts.firstOrNull { it.id == id } }
    if (editingId != null) {
        ContactEditor(
            contact = editing ?: Contact(id = editingId!!, addressBookId = addressBooks.firstOrNull()?.id ?: "local"),
            isNew = editing == null,
            onDismiss = { editingId = null },
            onSave = { value ->
                // Only the fields this editor puts on screen. The rest --
                // tags, title, the second and third phone number -- belong to
                // the contact, not to this form, and taking them from a draft
                // that never held them would delete them on a name change.
                if (editing == null) onCreate(value) else onUpdate(editing.copy(
                    displayName = value.displayName,
                    givenName = value.givenName,
                    familyName = value.familyName,
                    organization = value.organization,
                    emails = editing.emails.withEditedFirst(value.emails.firstOrNull()) { kept, edited -> kept.copy(value = edited.value) },
                    phones = editing.phones.withEditedFirst(value.phones.firstOrNull()) { kept, edited -> kept.copy(value = edited.value) },
                    birthday = value.birthday,
                    anniversary = value.anniversary,
                    note = value.note,
                ))
                editingId = null
            },
            onDelete = if (editing == null) null else { { onDelete(editing); editingId = null } },
        )
    }
}

/**
 * The editor shows one email and one phone: the first of each. Put the edited
 * text back on that entry and leave the rest of the list alone, so a changed
 * number keeps the label the server gave it -- Mobile stays Mobile -- and the
 * second and third numbers survive a save. A cleared field drops that entry;
 * text typed where the contact had none arrives as the editor built it.
 */
private fun <T> List<T>.withEditedFirst(edited: T?, onKept: (T, T) -> T): List<T> {
    val kept = firstOrNull()
    return when {
        edited == null -> drop(1)
        kept == null -> listOf(edited)
        else -> listOf(onKept(kept, edited)) + drop(1)
    }
}

private val ContactsListPaneWidth = 360.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactDirectory(
    contacts: List<Contact>,
    addressBooks: List<ContactAddressBook>,
    bookFilter: String?,
    tagFilter: String?,
    tags: List<String>,
    query: String,
    onQueryChanged: (String) -> Unit,
    onBookFilterChanged: (String?) -> Unit,
    onTagFilterChanged: (String?) -> Unit,
    onContact: (Contact) -> Unit,
    selectedId: String?,
    onOpenMenu: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onOpenMenu?.let { MenuButton(onClick = it, modifier = Modifier.padding(end = 6.dp)) }
            Text("Contacts", style = CalinoTypography.displayLarge, modifier = Modifier.weight(1f))
        }
        CalinoSearchField(
            query = query,
            onQueryChanged = onQueryChanged,
            placeholder = "Search people, numbers, tags…",
            contentDescription = "Search contacts",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        if (addressBooks.size > 1) {
            ContactFilterRow("Address books", addressBooks.map { it.id to it.name }, bookFilter, onBookFilterChanged)
        }
        if (tags.isNotEmpty()) {
            ContactTagRow(tags, tagFilter, onTagFilterChanged)
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = CalinoColors.Line)
        if (contacts.isEmpty()) {
            ContactEmptySearch(query)
        } else {
            val groups = groupContactsByAlpha(contacts)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = CalinoSpacing.PillClearance),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                groups.forEach { (key, members) ->
                    item(key = "contact-section:$key") {
                        Text(key, style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(top = 6.dp, bottom = 1.dp))
                    }
                    items(members, key = { "contact:${it.id}" }) { contact ->
                        ContactCard(contact, selected = selectedId == contact.id, onClick = { onContact(contact) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactFilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(end = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(end = 20.dp)) {
            item { CalinoChip("All", selected == null, "show every address book", { onSelected(null) }) }
            items(options, key = { it.first }) { (id, name) -> CalinoChip(name, selected == id, "filter by $name", { onSelected(id) }) }
        }
    }
}

@Composable
private fun ContactTagRow(tags: List<String>, selected: String?, onSelected: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(tags, key = { it }) { tag -> CalinoChip("#$tag", selected == tag, "filter contacts by $tag", { onSelected(if (selected == tag) null else tag) }) }
    }
}

@Composable
private fun ContactCard(contact: Contact, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(if (selected) CalinoColors.AccentSoft else CalinoColors.Panel, tween(170), label = "contact selection")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(CalinoShapes.Card))
            .background(background)
            .border(1.dp, if (selected) CalinoColors.Accent.copy(.25f) else CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open contact ${contact.derivedDisplayName()}"
                role = Role.Button
                this.selected = selected
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContactAvatar(contact)
        Column(Modifier.weight(1f)) {
            Text(contact.derivedDisplayName(), style = CalinoTypography.titleSmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.organization.ifBlank { contact.emails.primaryContactEmail()?.value ?: contact.phones.firstOrNull()?.value ?: "Contact" },
                style = CalinoTypography.bodySmall,
                color = CalinoColors.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (contact.isGroup) Text("GROUP", style = CalinoTypography.labelSmall, color = CalinoColors.Accent, fontSize = 9.sp)
    }
}

@Composable
private fun ContactAvatar(contact: Contact, modifier: Modifier = Modifier) {
    val name = contact.derivedDisplayName()
    val palette = listOf(
        CalinoColors.AccentSoft,
        CalinoColors.Blue.copy(.14f),
        CalinoColors.Green.copy(.14f),
        CalinoColors.Amber.copy(.14f),
        CalinoColors.Plum.copy(.14f),
    )
    val color = palette[(name.hashCode().toUInt().toInt() and Int.MAX_VALUE) % palette.size]
    val bitmap = remember(contact.photo) {
        contact.photo?.substringAfter("base64,", "")?.let { encoded ->
            runCatching { java.util.Base64.getDecoder().decode(encoded) }
                .getOrNull()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }
    Box(modifier.size(46.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription = "Photo of $name", modifier = Modifier.fillMaxSize())
        else Text(initials(name), style = CalinoTypography.titleMedium, color = CalinoColors.Ink)
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
private fun ContactDetailPane(
    contact: Contact,
    events: List<CalEvent>,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTag: (String) -> Unit,
    onAddDate: (LocalDate, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pillInLane: Boolean = false,
) {
    val context = LocalContext.current
    fun openContactLink(action: String, value: String) {
        runCatching { context.startActivity(Intent(action, Uri.parse(value))) }
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            // In the lane the pill floats over the card, so the list runs the
            // full height and the tail keeps the lane's clearance instead.
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = if (pillInLane) CalinoSpacing.PillClearance else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "contact-heading") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to contacts" }) { Icon(CalinoIcons.ChevronLeft, null, tint = CalinoColors.Ink) }
                    ContactAvatar(contact, Modifier.padding(start = 2.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(contact.derivedDisplayName(), style = CalinoTypography.headlineMedium)
                        contact.organization.takeIf(String::isNotBlank)?.let { Text(it, style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2) }
                    }
                }
            }
            item(key = "contact-network") {
                contact.emails.forEach { email ->
                    DetailRow(
                        CalinoIcon.Note,
                        "Email${if (email.isPrimary) " · primary" else ""}",
                        email.value,
                        onClick = { openContactLink(Intent.ACTION_SENDTO, "mailto:${email.value}") },
                    )
                }
                contact.phones.forEach { phone ->
                    DetailRow(CalinoIcon.Users, phone.type.label(), phone.value, onClick = { openContactLink(Intent.ACTION_DIAL, "tel:${phone.value}") })
                }
                contact.urls.forEach { url ->
                    val target = if (url.value.contains("://")) url.value else "https://${url.value}"
                    DetailRow(CalinoIcon.Pin, "Website", url.value, onClick = { openContactLink(Intent.ACTION_VIEW, target) })
                }
                contact.ims.forEach { im ->
                    DetailRow(CalinoIcon.Users, im.protocol, im.value, onClick = { openContactLink(Intent.ACTION_VIEW, im.value) })
                }
            }
            contact.addresses.forEachIndexed { index, address ->
                item(key = "address:$index") { DetailRow(CalinoIcon.Pin, "Address", listOf(address.street, address.city, address.region, address.postalCode, address.country).filter(String::isNotBlank).joinToString(", ")) }
            }
            contact.langs.forEachIndexed { index, lang -> item(key = "language:$index") { DetailRow(CalinoIcon.Note, "Language", lang.value) } }
            contact.related.forEachIndexed { index, relation -> item(key = "related:$index") { DetailRow(CalinoIcon.Users, relation.type.name, relation.value) } }
            if (contact.isGroup) item(key = "members") { DetailRow(CalinoIcon.Users, "Members", "${contact.memberUids.size} contacts") }
            contact.birthday?.let { date ->
                item(key = "birthday") { ContactDateRow("🎂", "Birthday", date, contact, events, today, false, onAddDate) }
            }
            contact.anniversary?.let { date ->
                item(key = "anniversary") { ContactDateRow("♥", "Anniversary", date, contact, events, today, true, onAddDate) }
            }
            if (contact.categories.isNotEmpty()) {
                item(key = "tags") {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                        Text("TAGS", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 7.dp)) {
                            items(contact.categories, key = { it }) { tag -> CalinoChip("#$tag", false, "filter by $tag", { onTag(tag) }) }
                        }
                    }
                }
            }
            if (contact.note.isNotBlank()) item(key = "notes") { DetailRow(CalinoIcon.Note, "Notes", contact.note) }
        }
        if (!pillInLane) {
            // The split layout has no card and no lane: the pane keeps its
            // own pill in flow, where it has always been.
            ContactDetailPill(
                expanded = true,
                inPillLane = false,
                onBack = onBack,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 20.dp),
            )
        }
    }
}

/** Contact detail's actions, in the pill lane or in the pane's own flow. */
@Composable
private fun ContactDetailPill(
    expanded: Boolean,
    inPillLane: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    ModalActionPill(
        addLabel = "New contact",
        morphFromAddPill = true,
        inPillLane = inPillLane,
        expanded = expanded,
        cancelLabel = "Cancel",
        onCancel = onBack,
        cancelDescription = "Close contact details",
        primaryLabel = "Edit",
        onPrimary = onEdit,
        primaryDescription = "Edit contact",
        deleteLabel = "Delete",
        onDelete = onDelete,
        deleteDescription = "Delete contact",
        deleteConfirmationActive = confirmingDelete,
        onDeleteConfirmationChange = { confirmingDelete = it },
        deleteHoldToConfirm = true,
        modifier = modifier,
    )
}

@Composable
private fun ContactDateRow(
    emoji: String,
    label: String,
    date: LocalDate,
    contact: Contact,
    events: List<CalEvent>,
    today: LocalDate,
    anniversary: Boolean,
    onAddDate: (LocalDate, Boolean) -> Unit,
) {
    val days = daysUntilNextContactDate(date, today)
    val onCalendar = hasContactEvent(contact.id, events, anniversary)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).clip(RoundedCornerShape(CalinoShapes.Card)).background(CalinoColors.Panel).border(1.dp, CalinoColors.Line, RoundedCornerShape(CalinoShapes.Card)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(label, style = CalinoTypography.labelLarge)
                Text(date.format(ContactDateFormat), style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 2.dp))
                Text(if (days == 0L) "Today" else "In $days days · ${contactAge(date, today)} years old", style = CalinoTypography.bodySmall, color = CalinoColors.Accent, modifier = Modifier.padding(top = 2.dp))
            }
            TextButton(onClick = { if (!onCalendar) onAddDate(date, anniversary) }, enabled = !onCalendar, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(if (onCalendar) "✓ On calendar" else "Add to calendar", color = if (onCalendar) CalinoColors.Ink3 else CalinoColors.Accent)
            }
        }
        if (!onCalendar) Text("Saved in this app only; it will not sync to the server.", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3, modifier = Modifier.padding(start = 34.dp, top = 5.dp))
    }
}

@Composable
private fun ContactEditor(
    contact: Contact,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (NewContact) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var displayName by rememberSaveable(contact.id) { mutableStateOf(contact.displayName) }
    var givenName by rememberSaveable(contact.id) { mutableStateOf(contact.givenName) }
    var familyName by rememberSaveable(contact.id) { mutableStateOf(contact.familyName) }
    var organization by rememberSaveable(contact.id) { mutableStateOf(contact.organization) }
    var email by rememberSaveable(contact.id) { mutableStateOf(contact.emails.firstOrNull()?.value.orEmpty()) }
    var phone by rememberSaveable(contact.id) { mutableStateOf(contact.phones.firstOrNull()?.value.orEmpty()) }
    var birthdayText by rememberSaveable(contact.id) { mutableStateOf(contact.birthday?.toString().orEmpty()) }
    var anniversaryText by rememberSaveable(contact.id) { mutableStateOf(contact.anniversary?.toString().orEmpty()) }
    var note by rememberSaveable(contact.id) { mutableStateOf(contact.note) }
    var showDiscard by rememberSaveable(contact.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(contact.id) { mutableStateOf(false) }
    var shown by remember(contact.id) { mutableStateOf(true) }
    var closeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dirty = displayName != contact.displayName || givenName != contact.givenName || familyName != contact.familyName || organization != contact.organization || email != contact.emails.firstOrNull()?.value.orEmpty() || phone != contact.phones.firstOrNull()?.value.orEmpty() || birthdayText != contact.birthday?.toString().orEmpty() || anniversaryText != contact.anniversary?.toString().orEmpty() || note != contact.note
    val canSave = listOf(displayName, givenName, familyName, organization, email, phone, birthdayText, anniversaryText, note).any { it.isNotBlank() }

    fun closeAnimated(action: () -> Unit) {
        if (shown) { closeAction = action; shown = false }
    }
    LaunchedEffect(shown) { if (!shown) { kotlinx.coroutines.delay(220); closeAction?.invoke() } }
    fun dismiss() { if (dirty) showDiscard = true else closeAnimated(onDismiss) }
    fun saveContact() {
        val parsedBirthday = parseContactDate(birthdayText)
        val parsedAnniversary = parseContactDate(anniversaryText)
        closeAnimated {
            onSave(NewContact(
                displayName = displayName.trim(), givenName = givenName.trim(), familyName = familyName.trim(), organization = organization.trim(),
                emails = email.trim().takeIf(String::isNotEmpty)?.let { listOf(ContactEmail(it, ContactType.Other, true)) } ?: emptyList(),
                phones = phone.trim().takeIf(String::isNotEmpty)?.let { listOf(ContactPhone(it, ContactPhoneType.Other, true)) } ?: emptyList(),
                birthday = parsedBirthday, anniversary = parsedAnniversary, note = note.trim(), addressBookId = contact.addressBookId,
            ))
        }
    }

    BottomDetailCard(
        visible = shown,
        onDismiss = ::dismiss,
        surfaceKind = CalinoSurfaceKind.Editor,
        resetKey = showDiscard,
        pill = {
            ModalActionPill(
                addLabel = if (isNew) "New contact" else "Edit contact",
                morphFromAddPill = true,
                inPillLane = true,
                expanded = shown,
                cancelLabel = "Cancel",
                onCancel = ::dismiss,
                cancelDescription = "Cancel contact editing",
                deleteLabel = onDelete?.let { "Delete" },
                onDelete = onDelete?.let { delete -> { showDelete = false; closeAnimated(delete) } },
                deleteDescription = "Delete contact",
                deleteConfirmationActive = showDelete,
                onDeleteConfirmationChange = { showDelete = it },
                deleteHoldToConfirm = true,
                primaryLabel = "Save",
                onPrimary = ::saveContact,
                primaryVisible = isNew || dirty,
                primaryEnabled = canSave,
                primaryDescription = "Save contact",
            )
        },
        content = { modifier ->
            Column(modifier.fillMaxSize().background(CalinoColors.Canvas)) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::dismiss, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to contacts" }) { Icon(CalinoIcons.ChevronLeft, null, tint = CalinoColors.Ink) }
                    Text(if (isNew) "New contact" else "Edit contact", style = CalinoTypography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 7.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CalinoColors.Line))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, top = 15.dp, end = 20.dp, bottom = CalinoSpacing.PillClearance), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { CalinoTextField(displayName, { displayName = it }, "Display name", placeholder = "Full name") }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CalinoTextField(givenName, { givenName = it }, "Given", Modifier.weight(1f)); CalinoTextField(familyName, { familyName = it }, "Family", Modifier.weight(1f)) } }
                    item { CalinoTextField(organization, { organization = it }, "Organization", placeholder = "Where they work") }
                    item { CalinoTextField(email, { email = it }, "Email", placeholder = "name@example.com") }
                    item { CalinoTextField(phone, { phone = it }, "Phone", placeholder = "+45 …") }
                    item { CalinoTextField(birthdayText, { birthdayText = it }, "Birthday", placeholder = "YYYY-MM-DD") }
                    item { CalinoTextField(anniversaryText, { anniversaryText = it }, "Anniversary", placeholder = "YYYY-MM-DD") }
                    item { CalinoTextField(note, { note = it }, "Notes", singleLine = false, minLines = 4, maxLines = 8) }
                    if (showDiscard) item {
                        Row(Modifier.fillMaxWidth().background(CalinoColors.Ink).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Discard your changes?", color = CalinoColors.Panel, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showDiscard = false }) { Text("Keep editing", color = CalinoColors.Panel) }
                            TextButton(onClick = { closeAnimated(onDismiss) }) { Text("Discard", color = CalinoColors.AccentSoft) }
                        }
                    }
                }
                // Room for the pill, which stands in the pill lane outside
                // this card so it can change shape there instead of leaving.
            }
        },
    )
}

private fun parseContactDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()

private fun ContactPhoneType.label(): String = when (this) {
    ContactPhoneType.Cell -> "Mobile"
    ContactPhoneType.Fax -> "Fax"
    ContactPhoneType.Home -> "Home phone"
    ContactPhoneType.Work -> "Work phone"
    ContactPhoneType.Pref -> "Primary phone"
    ContactPhoneType.Other -> "Phone"
}

@Composable
private fun ContactPaneEmpty() {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(CalinoColors.AccentSoft), contentAlignment = Alignment.Center) { Icon(CalinoIcons.Users, null, tint = CalinoColors.Accent) }
        Text("Choose a contact", style = CalinoTypography.titleMedium, modifier = Modifier.padding(top = 14.dp))
        Text("Their details will stay beside the directory.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ContactEmptySearch(query: String) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (query.isBlank()) "No contacts yet" else "No contacts found", style = CalinoTypography.titleMedium)
        Text(if (query.isBlank()) "Add someone to your directory." else "Try a name, number, or tag.", style = CalinoTypography.bodyMedium, color = CalinoColors.Ink2, modifier = Modifier.padding(top = 4.dp))
    }
}
