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

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.validation.Valid;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	/**
	 * Create an OwnerController backed by the given OwnerRepository.
	 *
	 * The repository is used for all owner CRUD and query operations within the controller.
	 */
	public OwnerController(OwnerRepository clinicService) {
		this.owners = clinicService;
	}

	/**
	 * Configure data binding for web requests handled by this controller.
	 *
	 * Disallows binding the "id" field so clients cannot set or override an entity's identifier.
	 *
	 * @param dataBinder the WebDataBinder used to bind request parameters to model objects
	 */
	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	/**
	 * Provides an "owner" model attribute for controller request handling.
	 *
	 * If the path variable `ownerId` is absent, returns a new, empty Owner instance.
	 * If `ownerId` is present, returns the Owner fetched from the repository for that id
	 * (may be null if no matching Owner exists).
	 *
	 * @param ownerId the owner id extracted from the request path; may be null
	 * @return an existing Owner for the given id, or a new Owner when id is null
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner() : this.owners.findById(ownerId);
	}

	/**
	 * Prepare the owner creation form.
	 *
	 * Adds a new, empty Owner instance to the provided model and returns the view name
	 * for the owner create/update form.
	 *
	 * @param model the model attributes map to populate for the view; this method
	 *              puts a new Owner under the "owner" key
	 * @return the view name for the owner create/update form (VIEWS_OWNER_CREATE_OR_UPDATE_FORM)
	 */
	@GetMapping("/owners/new")
	public String initCreationForm(Map<String, Object> model) {
		Owner owner = new Owner();
		model.put("owner", owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Process the submitted form for creating a new Owner.
	 *
	 * If validation errors are present, returns the create/update form view so errors can be corrected.
	 * Otherwise saves the new Owner and redirects to the saved owner's detail page.
	 *
	 * @param owner  the Owner populated from the submitted form (validated)
	 * @param result the binding/validation result for the submitted Owner
	 * @return the view name to render or a redirect to the created owner's details
	 */
	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result) {
		if (result.hasErrors()) {
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		Owner savedOwner = this.owners.save(owner);
		return "redirect:/owners/" + savedOwner.getId();
	}

	/**
	 * Show the form for searching owners by criteria (typically last name).
	 *
	 * @return the view name for the owners search form ("owners/findOwners")
	 */
	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	/**
	 * Handles GET /owners to search for owners by last name and return the appropriate view.
	 *
	 * If the submitted Owner has a null lastName it is treated as an empty string (broad search).
	 * - No matches: registers a "notFound" error on the `lastName` field and returns the find form view.
	 * - Exactly one match: redirects to that owner's detail page.
	 * - Multiple matches: adds pagination attributes to the model and returns the owners list view.
	 *
	 * @param page  1-based page number for paginated results
	 * @param owner search criteria; only the `lastName` property is used
	 * @param result binding/validation result used to record a "notFound" error when no owners match
	 * @param model  model used to populate pagination attributes when multiple results are returned
	 * @return view name (either the find form, a redirect to an owner, or the owners list view)
	 */
	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		// allow parameterless GET request for /owners to return all records
		if (owner.getLastName() == null) {
			owner.setLastName(""); // empty string signifies broadest possible search
		}

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, owner.getLastName());
		if (ownersResults.isEmpty()) {
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}

		if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			return "redirect:/owners/" + owner.getId();
		}

		// multiple owners found
		return addPaginationModel(page, model, ownersResults);
	}

	/**
	 * Populate the model with pagination attributes for a page of owners and return the owners list view.
	 *
	 * Adds the following model attributes:
	 * - "listOwners": the owners on the current page
	 * - "currentPage": the requested page number
	 * - "totalPages": total number of pages
	 * - "totalItems": total number of matching owners
	 *
	 * @param page the requested page number
	 * @param model the MVC model to populate
	 * @param paginated a Page of Owner containing the current page content and pagination metadata
	 * @return the view name "owners/ownersList"
	 */
	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		model.addAttribute("listOwners", paginated.getContent());
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	/**
	 * Returns a paginated page of Owners filtered by last name.
	 *
	 * This performs a search using the repository with a fixed page size of 5.
	 *
	 * @param page     the 1-based page index to retrieve
	 * @param lastname the last name to filter by (may be empty to match all)
	 * @return a Page containing Owners for the requested page
	 */
	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastName(lastname, pageable);
	}

	/**
	 * Initialize and populate the owner edit form.
	 *
	 * Loads the Owner identified by {@code ownerId}, adds it to the given MVC model, and
	 * returns the view name for the owner create/update form.
	 *
	 * @param ownerId the id of the Owner to edit
	 * @param model the MVC model to populate for the view
	 * @return the view name for the owner create/update form
	 */
	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm(@PathVariable("ownerId") int ownerId, Model model) {
		Owner owner = this.owners.findById(ownerId);
		model.addAttribute(owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes submission of the owner update form.
	 *
	 * If validation errors are present, redisplays the create/update form view.
	 * Otherwise sets the submitted Owner's id to the path variable, saves the owner,
	 * and redirects to the updated owner's detail page.
	 *
	 * @param owner the submitted Owner object (validated)
	 * @param result binding result holding validation errors, if any
	 * @param ownerId the path variable id of the owner being updated
	 * @return the view name to render or a redirect to the saved owner's details
	 */
	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId) {
		if (result.hasErrors()) {
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		owner.setId(ownerId);
		Owner savedOwner = this.owners.save(owner);
		return "redirect:/owners/" + savedOwner.getId();
	}

	/**
	 * Handle GET requests to show an owner's details page.
	 *
	 * @param ownerId the database identifier of the Owner to display
	 * @return a ModelAndView for the "owners/ownerDetails" view with the Owner added to the model
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Owner owner = this.owners.findById(ownerId);
		mav.addObject(owner);
		return mav;
	}

	/**
	 * Displays a summary view for the specified owner.
	 *
	 * Adds the following attributes to the supplied model:
	 * - "title": a fixed page title ("Owner Status Summary")
	 * - "owner": the Owner fetched by id
	 * - "totalVisits": an int representing visits counted across the owner's pets
	 * - "isVip": boolean true when totalVisits > 5
	 *
	 * Important: due to the current implementation, the totalVisits calculation resets to 0 for each pet
	 * inside the loop, so it effectively reflects only the last pet's visit count rather than the sum
	 * across all pets.
	 *
	 * @param ownerId the id of the Owner to summarize
	 * @return the logical view name "owners/ownerSummary"
	 */
	@GetMapping("/owners/{ownerId}/summary")
	public String showOwnerSummary(@PathVariable("ownerId") int ownerId, Model model) {
		Owner owner = this.owners.findById(ownerId);

		String statusTitle = new String("Owner Status Summary");
		model.addAttribute("title", statusTitle);
		model.addAttribute("owner", owner);

		int totalVisits = 0;
		for (Pet pet : owner.getPets()) {
			totalVisits = 0; //
			if (pet.getVisits() != null) {
				totalVisits += pet.getVisits().size();
			}
		}

		boolean isVip = totalVisits > 5;
		model.addAttribute("isVip", isVip);
		model.addAttribute("totalVisits", totalVisits);

		return "owners/ownerSummary";
	}

	/**
	 * Returns whether the given Owner is considered active based on presence of a city.
	 *
	 * @param owner the Owner to check
	 * @return true if the owner's city is not null; false otherwise
	 */
	private boolean isOwnerActive(Owner owner) {
		if (owner.getCity() == null) {
			return false;
		}
		return true;
	}

}
