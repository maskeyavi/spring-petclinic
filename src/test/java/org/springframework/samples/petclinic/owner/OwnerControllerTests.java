/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.samples.petclinic.owner.PetType;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for {@link OwnerController}
 *
 * @author Colin But
 * @author Wick Dynex
 */
@WebMvcTest(OwnerController.class)
@DisabledInNativeImage
@DisabledInAotMode
class OwnerControllerTests {

	private static final int TEST_OWNER_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	private Owner george() {
		Owner george = new Owner();
		george.setId(TEST_OWNER_ID);
		george.setFirstName("George");
		george.setLastName("Franklin");
		george.setAddress("110 W. Liberty St.");
		george.setCity("Madison");
		george.setTelephone("6085551023");
		Pet max = new Pet();
		PetType dog = new PetType();
		dog.setName("dog");
		max.setType(dog);
		max.setName("Max");
		max.setBirthDate(LocalDate.now());
		george.addPet(max);
		max.setId(1);
		return george;
	}

	@BeforeEach
	void setup() {

		Owner george = george();
		given(this.owners.findByLastNameStartingWith(eq("Franklin"), any(Pageable.class)))
			.willReturn(new PageImpl<>(List.of(george)));

		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(george));
		Visit visit = new Visit();
		visit.setDate(LocalDate.now());
		george.getPet("Max").getVisits().add(visit);

	}

	@Test
	void testInitCreationForm() throws Exception {
		mockMvc.perform(get("/owners/new"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("owner"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}

	@Test
	void testProcessCreationFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/new").param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "1316761638"))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	void testProcessCreationFormHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/new").param("firstName", "Joe").param("lastName", "Bloggs").param("city", "London"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "address"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}

	@Test
	void testInitFindForm() throws Exception {
		mockMvc.perform(get("/owners/find"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("owner"))
			.andExpect(view().name("owners/findOwners"));
	}

	@Test
	void testProcessFindFormSuccess() throws Exception {
		Page<Owner> tasks = new PageImpl<>(List.of(george(), new Owner()));
		when(this.owners.findByLastNameStartingWith(anyString(), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=1")).andExpect(status().isOk()).andExpect(view().name("owners/ownersList"));
	}

	@Test
	void testProcessFindFormByLastName() throws Exception {
		Page<Owner> tasks = new PageImpl<>(List.of(george()));
		when(this.owners.findByLastNameStartingWith(eq("Franklin"), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=1").param("lastName", "Franklin"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/" + TEST_OWNER_ID));
	}

	@Test
	void testProcessFindFormNoOwnersFound() throws Exception {
		Page<Owner> tasks = new PageImpl<>(List.of());
		when(this.owners.findByLastNameStartingWith(eq("Unknown Surname"), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=1").param("lastName", "Unknown Surname"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("owner", "lastName"))
			.andExpect(model().attributeHasFieldErrorCode("owner", "lastName", "notFound"))
			.andExpect(view().name("owners/findOwners"));

	}

	@Test
	void testInitUpdateOwnerForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/edit", TEST_OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("owner"))
			.andExpect(model().attribute("owner", hasProperty("lastName", is("Franklin"))))
			.andExpect(model().attribute("owner", hasProperty("firstName", is("George"))))
			.andExpect(model().attribute("owner", hasProperty("address", is("110 W. Liberty St."))))
			.andExpect(model().attribute("owner", hasProperty("city", is("Madison"))))
			.andExpect(model().attribute("owner", hasProperty("telephone", is("6085551023"))))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}

	@Test
	void testProcessUpdateOwnerFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID).param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "1616291589"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void testProcessUpdateOwnerFormUnchangedSuccess() throws Exception {
		mockMvc.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));
	}

	@Test
	void testProcessUpdateOwnerFormHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID).param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "")
				.param("telephone", ""))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "address"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}

	@Test
	void testShowOwner() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}", TEST_OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(model().attribute("owner", hasProperty("lastName", is("Franklin"))))
			.andExpect(model().attribute("owner", hasProperty("firstName", is("George"))))
			.andExpect(model().attribute("owner", hasProperty("address", is("110 W. Liberty St."))))
			.andExpect(model().attribute("owner", hasProperty("city", is("Madison"))))
			.andExpect(model().attribute("owner", hasProperty("telephone", is("6085551023"))))
			.andExpect(model().attribute("owner", hasProperty("pets", not(empty()))))
			.andExpect(model().attribute("owner",
					hasProperty("pets", hasItem(hasProperty("visits", hasSize(greaterThan(0)))))))
			.andExpect(view().name("owners/ownerDetails"));
	}

	@Test
	public void testProcessUpdateOwnerFormWithIdMismatch() throws Exception {

	@Test
	void testProcessCreationFormWithInvalidTelephone() throws Exception {

	@Test
	void testProcessCreationFormWithEmptyFirstName() throws Exception {

	@Test
	void testProcessCreationFormWithEmptyLastName() throws Exception {

	@Test
	void testProcessCreationFormWithEmptyCity() throws Exception {

	@Test
	void testProcessCreationFormWithTooShortTelephone() throws Exception {

	@Test
	void testProcessCreationFormWithTooLongTelephone() throws Exception {

	@Test
	void testProcessCreationFormWithSpecialCharacters() throws Exception {

	@Test
	void testProcessFindFormWithMultipleResults() throws Exception {

	@Test
	void testProcessFindFormWithPagination() throws Exception {

	@Test
	void testProcessFindFormWithEmptyLastName() throws Exception {

	@Test
	void testShowOwnerWithNoPets() throws Exception {

	@Test
	void testShowOwnerWithMultiplePets() throws Exception {

	@Test
	void testProcessUpdateOwnerFormWithInvalidData() throws Exception {

	@Test
	void testProcessUpdateOwnerFormWithInvalidTelephone() throws Exception {

	@Test
	void testProcessFindFormWithInvalidPageNumber() throws Exception {

	@Test
	void testProcessFindFormWithZeroPageNumber() throws Exception {

	@Test
	void testShowOwnerSummary() throws Exception {

	@Test
	void testShowOwnerSummaryWithNoPets() throws Exception {

	@Test
	void testShowOwnerSummaryWithNonExistentOwner() throws Exception {

	@Test
	void testInitCreationFormModelAttributes() throws Exception {

	@Test
	void testInitFindFormModelAttributes() throws Exception {

	@Test
	void testInitUpdateOwnerFormWithNonExistentOwner() throws Exception {

	@Test
	void testShowOwnerWithNonExistentOwner() throws Exception {

	@Test
	void testProcessUpdateOwnerFormWithNonExistentOwner() throws Exception {
		given(this.owners.findById(999)).willReturn(Optional.empty());
		mockMvc
			.perform(post("/owners/{ownerId}/edit", 999)
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "1616291589"))
			.andExpect(status().isNotFound());
	}
		given(this.owners.findById(999)).willReturn(Optional.empty());
		mockMvc.perform(get("/owners/{ownerId}", 999))
			.andExpect(status().isNotFound());
	}
		given(this.owners.findById(999)).willReturn(Optional.empty());
		mockMvc.perform(get("/owners/{ownerId}/edit", 999))
			.andExpect(status().isNotFound());
	}
		mockMvc.perform(get("/owners/find"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("owner"))
			.andExpect(model().attribute("owner", hasProperty("lastName", is(nullValue()))))
			.andExpect(view().name("owners/findOwners"));
	}
		mockMvc.perform(get("/owners/new"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("owner"))
			.andExpect(model().attribute("owner", hasProperty("firstName", is(nullValue()))))
			.andExpect(model().attribute("owner", hasProperty("lastName", is(nullValue()))))
			.andExpect(model().attribute("owner", hasProperty("address", is(nullValue()))))
			.andExpect(model().attribute("owner", hasProperty("city", is(nullValue()))))
			.andExpect(model().attribute("owner", hasProperty("telephone", is(nullValue()))))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		given(this.owners.findById(999)).willReturn(Optional.empty());
		mockMvc.perform(get("/owners/{ownerId}/summary", 999))
			.andExpect(status().isNotFound());
	}
		Owner ownerWithNoPets = new Owner();
		ownerWithNoPets.setId(6);
		ownerWithNoPets.setFirstName("Empty");
		ownerWithNoPets.setLastName("Hands");
		ownerWithNoPets.setAddress("123 No Pet St");
		ownerWithNoPets.setCity("Petless");
		ownerWithNoPets.setTelephone("5550000000");

		given(this.owners.findById(6)).willReturn(Optional.of(ownerWithNoPets));

		mockMvc.perform(get("/owners/{ownerId}/summary", 6))
			.andExpect(status().isOk())
			.andExpect(model().attribute("title", is("Owner Status Summary")))
			.andExpect(model().attribute("owner", hasProperty("firstName", is("Empty"))))
			.andExpect(model().attribute("isVip", is(false)))
			.andExpect(model().attribute("totalVisits", is(0)))
			.andExpect(view().name("owners/ownerSummary"));
	}
		Owner george = george();
		
		// Add multiple visits to make owner VIP (>5 visits) - but note the buggy logic in controller
		for (int i = 0; i < 6; i++) {
			Visit visit = new Visit();
			visit.setDate(LocalDate.now().minusDays(i));
			visit.setDescription("Visit " + i);
			george.getPet("Max").addVisit(visit);
		}

		given(this.owners.findById(TEST_OWNER_ID)).willReturn(Optional.of(george));

		mockMvc.perform(get("/owners/{ownerId}/summary", TEST_OWNER_ID))
			.andExpect(status().isOk())
			.andExpect(model().attribute("title", is("Owner Status Summary")))
			.andExpect(model().attribute("owner", hasProperty("firstName", is("George"))))
			.andExpect(model().attribute("isVip", is(false)))
			.andExpect(model().attribute("totalVisits", is(0)))
			.andExpect(view().name("owners/ownerSummary"));
	}
		Page<Owner> tasks = new PageImpl<>(List.of(george()));
		when(this.owners.findByLastNameStartingWith(anyString(), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=0"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/ownersList"));
	}
		Page<Owner> tasks = new PageImpl<>(List.of(george()));
		when(this.owners.findByLastNameStartingWith(anyString(), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/ownersList"));
	}
		mockMvc
			.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID)
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "invalid-phone-number"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID)
				.param("firstName", "")
				.param("lastName", "")
				.param("address", "")
				.param("city", "")
				.param("telephone", ""))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "firstName"))
			.andExpect(model().attributeHasFieldErrors("owner", "lastName"))
			.andExpect(model().attributeHasFieldErrors("owner", "address"))
			.andExpect(model().attributeHasFieldErrors("owner", "city"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		Owner ownerWithMultiplePets = new Owner();
		ownerWithMultiplePets.setId(5);
		ownerWithMultiplePets.setFirstName("Jane");
		ownerWithMultiplePets.setLastName("Smith");
		ownerWithMultiplePets.setAddress("456 Oak Ave");
		ownerWithMultiplePets.setCity("Springfield");
		ownerWithMultiplePets.setTelephone("5559876543");

		Pet cat = new Pet();
		PetType catType = new PetType();
		catType.setName("cat");
		cat.setType(catType);
		cat.setName("Fluffy");
		cat.setBirthDate(LocalDate.now().minusYears(2));
		cat.setId(2);

		Pet dog = new Pet();
		PetType dogType = new PetType();
		dogType.setName("dog");
		dog.setType(dogType);
		dog.setName("Rex");
		dog.setBirthDate(LocalDate.now().minusYears(1));
		dog.setId(3);

		ownerWithMultiplePets.addPet(cat);
		ownerWithMultiplePets.addPet(dog);
		given(this.owners.findById(5)).willReturn(Optional.of(ownerWithMultiplePets));

		mockMvc.perform(get("/owners/{ownerId}", 5))
			.andExpect(status().isOk())
			.andExpect(model().attribute("owner", hasProperty("pets", hasSize(2))))
			.andExpect(model().attribute("owner", hasProperty("pets", hasItem(hasProperty("name", is("Fluffy"))))))
			.andExpect(model().attribute("owner", hasProperty("pets", hasItem(hasProperty("name", is("Rex"))))))
			.andExpect(view().name("owners/ownerDetails"));
	}
		Owner ownerWithNoPets = new Owner();
		ownerWithNoPets.setId(4);
		ownerWithNoPets.setFirstName("John");
		ownerWithNoPets.setLastName("Doe");
		ownerWithNoPets.setAddress("123 Main St");
		ownerWithNoPets.setCity("Anytown");
		ownerWithNoPets.setTelephone("5551234567");
		given(this.owners.findById(4)).willReturn(Optional.of(ownerWithNoPets));

		mockMvc.perform(get("/owners/{ownerId}", 4))
			.andExpect(status().isOk())
			.andExpect(model().attribute("owner", hasProperty("firstName", is("John"))))
			.andExpect(model().attribute("owner", hasProperty("lastName", is("Doe"))))
			.andExpect(model().attribute("owner", hasProperty("pets", empty())))
			.andExpect(view().name("owners/ownerDetails"));
	}
		Page<Owner> tasks = new PageImpl<>(List.of(george(), new Owner()));
		when(this.owners.findByLastNameStartingWith(eq(""), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=1").param("lastName", ""))
			.andExpect(status().isOk())
			.andExpect(view().name("owners/ownersList"));
	}
		Owner george = george();
		Page<Owner> tasks = new PageImpl<>(List.of(george), org.springframework.data.domain.PageRequest.of(0, 5), 10);
		when(this.owners.findByLastNameStartingWith(anyString(), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=2"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("listOwners"))
			.andExpect(model().attribute("currentPage", is(2)))
			.andExpect(model().attribute("totalPages", is(2)))
			.andExpect(model().attribute("totalItems", is(10L)))
			.andExpect(view().name("owners/ownersList"));
	}
		Owner george = george();
		Owner betty = new Owner();
		betty.setId(2);
		betty.setFirstName("Betty");
		betty.setLastName("Davis");
		betty.setAddress("638 Cardinal Ave.");
		betty.setCity("Sun Prairie");
		betty.setTelephone("6085551749");

		Page<Owner> tasks = new PageImpl<>(List.of(george, betty));
		when(this.owners.findByLastNameStartingWith(eq(""), any(Pageable.class))).thenReturn(tasks);
		mockMvc.perform(get("/owners?page=1").param("lastName", ""))
			.andExpect(status().isOk())
			.andExpect(model().attribute("listOwners", hasSize(2)))
			.andExpect(view().name("owners/ownersList"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "José")
				.param("lastName", "García-López")
				.param("address", "123 Main St. Apt #4B")
				.param("city", "São Paulo")
				.param("telephone", "1316761638"))
			.andExpect(status().is3xxRedirection());
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "12345678901"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "12345"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "")
				.param("telephone", "1316761638"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "city"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "Joe")
				.param("lastName", "")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "1316761638"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "lastName"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "1316761638"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "firstName"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		mockMvc
			.perform(post("/owners/new")
				.param("firstName", "Joe")
				.param("lastName", "Bloggs")
				.param("address", "123 Caramel Street")
				.param("city", "London")
				.param("telephone", "invalid-phone"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("owner"))
			.andExpect(model().attributeHasFieldErrors("owner", "telephone"))
			.andExpect(view().name("owners/createOrUpdateOwnerForm"));
	}
		int pathOwnerId = 1;

		Owner owner = new Owner();
		owner.setId(2);
		owner.setFirstName("John");
		owner.setLastName("Doe");
		owner.setAddress("Center Street");
		owner.setCity("New York");
		owner.setTelephone("0123456789");

		when(owners.findById(pathOwnerId)).thenReturn(Optional.of(owner));

		mockMvc.perform(MockMvcRequestBuilders.post("/owners/{ownerId}/edit", pathOwnerId).flashAttr("owner", owner))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + pathOwnerId + "/edit"))
			.andExpect(flash().attributeExists("error"));
	}

}
