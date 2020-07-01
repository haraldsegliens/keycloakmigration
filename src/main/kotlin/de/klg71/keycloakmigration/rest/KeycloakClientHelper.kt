@file:Suppress("TooManyFunctions")

package de.klg71.keycloakmigration.rest

import de.klg71.keycloakmigration.changeControl.KeycloakException
import de.klg71.keycloakmigration.changeControl.actions.MigrationException
import de.klg71.keycloakmigration.model.Client
import de.klg71.keycloakmigration.model.GroupListItem
import de.klg71.keycloakmigration.model.Role
import feign.Response
import java.util.UUID

/**
 * File contains a lot of convenience functions when interacting with the keycloak clien
 */

fun KeycloakClient.userByName(name: String, realm: String) =
        searchByUsername(name, realm)
                .run {
                    if (isEmpty()) {
                        throw MigrationException("User with name: $name does not exist in $realm!")
                    }
                    first()
                }
                .run {
                    user(id, realm)
                }

fun KeycloakClient.clientById(clientId: String, realm: String): Client =
        clients(realm)
                .run {
                    if (isEmpty()) {
                        throw MigrationException("Client with id: $clientId does not exist in $realm!")
                    }
                    find { it.clientId == clientId }.let {
                        it ?: throw MigrationException("Client with id: $clientId does not exist in realm: $realm!")
                    }
                }.let {
                    client(it.id, realm)
                }

fun KeycloakClient.groupByName(name: String, realm: String) =
        searchGroup(name, realm)
                .run {
                    if (isEmpty()) {
                        throw MigrationException("Group with name: $name does not exist in realm: $realm!")
                    }
                    searchByName(name) ?: throw MigrationException("Group with name: $name does not exist in $realm")
                }.run {
                    group(realm, id)
                }

@Suppress("ReturnCount")
fun KeycloakClient.existsGroup(name: String, realm: String): Boolean =
        searchGroup(name, realm)
                .run {
                    if (isEmpty()) {
                        return false
                    }
                    if (searchByName(name) == null) {
                        return false
                    }
                    return true
                }

fun KeycloakClient.existsUser(name: String, realm: String): Boolean =
        searchUser(name, realm)
                .run {
                    if (isEmpty()) {
                        return false
                    }
                    return true
                }

@Suppress("ReturnCount")
fun KeycloakClient.existsClient(clientId: String, realm: String): Boolean =
        clients(realm)
                .run {
                    if (isEmpty()) {
                        return false
                    }
                    find { it.clientId == clientId }?.let {
                        return true
                    }
                    return false
                }

fun KeycloakClient.existsRole(name: String, realm: String): Boolean =
        roleByNameResponse(name, realm)
                .run {
                    if (isSuccessful()) {
                        return true
                    }
                    return false
                }

fun KeycloakClient.existsClientRole(name: String, realm: String, clientId: String): Boolean =
        clientRoles(realm, clientUUID(clientId, realm)).any {
            it.name == name
        }

private fun List<GroupListItem>.searchByName(name: String): GroupListItem? {
    return firstOrNull { it.name == name } ?: map { it.subGroups.searchByName(name) }.filterNotNull().firstOrNull()
}

fun KeycloakClient.clientRoleByName(name: String, clientId: String, realm: String): Role =
        clientById(clientId, realm)
                .run {
                    clientRoles(realm, id)
                }.run {
                    find { it.name == name }.let {
                        if (it == null) {
                            throw MigrationException(
                                    "Role with name: $name does not exist on client $clientId on realm $realm!")
                        }
                        clientRole(it.id, realm, UUID.fromString(it.containerId))
                    }
                }

fun KeycloakClient.userUUID(user: String, realm: String) = userByName(user, realm).id

fun KeycloakClient.groupUUID(group: String, realm: String) = groupByName(group, realm).id

fun KeycloakClient.clientUUID(clientId: String, realm: String) = clientById(clientId, realm).id

const val SUCCESSFUL_RESPONSE_START = 200
const val SUCCESSFUL_RESPONSE_END = 299
fun Response.isSuccessful() = when (status()) {
    in SUCCESSFUL_RESPONSE_START..SUCCESSFUL_RESPONSE_END -> true
    else -> false
}

fun Response.extractLocationUUID(): UUID {
    if (!isSuccessful()) {
        throw KeycloakException(this.body().asReader().readText())
    }
    return headers()["location"]!!.first()
            .run {
                split("/").last()
            }.let {
                UUID.fromString(it)
            }
}

fun KeycloakClient.realmById(id: String) =
        realms().firstOrNull { it.id == id } ?: throw MigrationException("Realm with id: $id does not exist!")

@Suppress("TooGenericExceptionCaught")
fun KeycloakClient.realmExistsById(id: String) =
        try {
            realms().any { it.id == id }
        } catch (e: Throwable) {
            // If you don't have the right permissions you will only get the realmnames back
            realmNames().map { it.realm }.contains(id)
        }

fun KeycloakClient.roleExistsByName(name: String, realm: String) = roles(realm).any { it.name == name }

fun KeycloakClient.roleExistsByName(name: String, realm: String, client: String) = clientRoles(realm,
        clientById(client, realm).id).any { it.name == name }

fun KeycloakClient.userFederationByName(name: String, realm: String) =
        userFederations(realm).firstOrNull { it.name == name }
                .let {
                    it ?: throw MigrationException("UserFederation with name: $name does not exist in $realm!")
                }

fun KeycloakClient.userFederationExistsByName(name: String, realm: String) =
        userFederations(realm).any { it.name == name }

fun KeycloakClient.mapperExistsByName(clientId: String, mapperName: String, realm: String) =
        mappers(clientUUID(clientId, realm), realm).any { it.name == mapperName }

fun KeycloakClient.ldapMapperByName(ldapName: String, name: String, realm: String) =
        ldapMappers(realm, userFederationByName(ldapName, realm).id).firstOrNull { it.name == name }
                .let {
                    it ?: throw MigrationException(
                            "UserFederationMapper with name: $name does not exist in $ldapName in $realm!")
                }

fun KeycloakClient.ldapMapperExistsByName(ldapName: String, name: String, realm: String) =
        ldapMappers(realm, userFederationByName(ldapName, realm).id).any { it.name == name }
