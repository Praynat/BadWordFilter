package com.example.explicitwordsfilter

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.explicitwordsfilter.ui.theme.ExplicitWordsFilterTheme

class ManageSiteListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExplicitWordsFilterTheme {
                ManageSiteListScreen()
            }
        }
    }
}

// Fonctions de chargement/sauvegarde
fun loadUserBlockedSites(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(SharedPrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getStringSet(SharedPrefsConstants.USER_SITES_KEY, emptySet()) ?: emptySet()
}

fun saveUserBlockedSites(context: Context, newSites: Set<String>) {
    val prefs = context.getSharedPreferences(SharedPrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(SharedPrefsConstants.USER_SITES_KEY, newSites).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSiteListScreen() {
    val context = LocalContext.current

    // Liste des sites
    var siteList by remember { mutableStateOf(loadUserBlockedSites(context).toList()) }

    // Champ de texte pour ajouter un nouveau site (toujours disponible)
    var newSite by remember { mutableStateOf("") }

    // Flag qui indique si on a débloqué l’accès (affichage) de la liste
    var isListUnlocked by remember { mutableStateOf(false) }

    // État du dialog de mot de passe
    var showPasswordDialog by remember { mutableStateOf(false) }

    // Saisie du mot de passe dans la Dialog
    var enteredPassword by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        // -- Zone d’ajout de site (pas protégée) --
        OutlinedTextField(
            value = newSite,
            onValueChange = { newSite = it },
            label = { Text("Enter site to block") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (newSite.isNotBlank()) {
                    val updatedList = siteList + newSite
                    siteList = updatedList
                    saveUserBlockedSites(context, updatedList.toSet())
                    newSite = ""
                    Toast.makeText(context, "Site added", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Add Site")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -- Bouton pour débloquer l’affichage de la liste --
        if (!isListUnlocked) {
            Button(onClick = { showPasswordDialog = true }) {
                Text("Unlock Blocked Sites")
            }
        } else {
            Text("Blocked sites are now visible", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -- Affichage conditionnel de la liste --
        if (isListUnlocked) {
            // On affiche la liste ET on autorise la suppression
            LazyColumn {
                items(siteList) { site ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(site)
                        IconButton(onClick = {
                            val updatedList = siteList - site
                            siteList = updatedList
                            saveUserBlockedSites(context, updatedList.toSet())
                            Toast.makeText(context, "Site deleted", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        } else {
            // Liste invisible : on peut mettre un message par exemple
            Text("Sites are hidden. Tap 'Unlock Blocked Sites' to see them.")
        }
    }

    // ----- Dialogue de saisie de mot de passe -----
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Enter Password") },
            text = {
                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = { enteredPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val savedPassword = PasswordManager.getPassword(context)
                    if (enteredPassword == savedPassword) {
                        isListUnlocked = true
                        Toast.makeText(context, "Sites unlocked", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                    showPasswordDialog = false
                    enteredPassword = ""
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    enteredPassword = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
