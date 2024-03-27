#!/bin/bash

#### SETUP ####

# The directory this file is in must contain a file named 'sar_endpoint_config' which includes the following fields.
# If the secret keys contain $ symbols, make sure to escape them with '\'
# client_with_sar_role=
# secret_for_client_with_sar_role=""

# client_without_sar_role=
# secret_for_client_without_sar_role=""

# expected_id_type= # prn/crn depending on which identifier the system expects
# unexpected_id_type= # prn/crn - whichever the system DOESN'T expect
# valid_id= # Nomis/nDelius ID that should return data from the system
# dummy_id= # Nomis/nDelius ID that should return no data from the system

###############

. sar_endpoint_config

# Get auth token with ROLE_SAR_DATA_ACCESS
auth_response_role=$(curl --no-progress-meter -s -X POST "https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token?grant_type=client_credentials" \ -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n $client_with_sar_role:$secret_for_client_with_sar_role | base64)")
role_token=$( echo $auth_response_role | grep "\"access_token\":*" | awk -F\: '{print $2}' | awk -F\, '{print $1}')
role_token_without_quotes=$(eval echo $role_token)

# Get auth token without ROLE_SAR_DATA_ACCESS
auth_response_no_role=$(curl -s --no-progress-meter -X POST "https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token?grant_type=client_credentials" \ -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n $client_without_sar_role:$secret_for_client_without_sar_role | base64)")
no_role_token=$(echo $auth_response_no_role | grep "\"access_token\":*" | awk -F\: '{print $2}' | awk -F\, '{print $1}')
no_role_token_without_quotes=$(eval echo $no_role_token)

# Declare endpoints
declare -a endpoints=(
https://complexity-of-need-staging.hmpps.service.justice.gov.uk 
# https://incident-reporting-api-dev.hmpps.service.justice.gov.uk 
# https://activities-api-dev.prison.service.justice.gov.uk 
# https://hdc-api-dev.hmpps.service.justice.gov.uk
# https://keyworker-api-dev.prison.service.justice.gov.uk
# https://restricted-patients-api-dev.hmpps.service.justice.gov.uk
# https://manage-adjudications-api-dev.hmpps.service.justice.gov.uk
# https://education-employment-api-dev.hmpps.service.justice.gov.uk
# https://create-and-vary-a-licence-api-dev.hmpps.service.justice.gov.uk
# https://dev.offender-case-notes.service.justice.gov.uk
# https://hmpps-book-secure-move-api-staging.apps.cloud-platform.service.justice.gov.uk
# https://learningandworkprogress-api-dev.hmpps.service.justice.gov.uk
# https://dev.moic.service.justice.gov.uk
# https://hmpps-uof-data-api-dev.hmpps.service.justice.gov.uk
)

for endpoint in "${endpoints[@]}"
do
   error_count=0
   echo "Testing endpoint: $endpoint"

   # Health response (should be 200)
   echo "/health (should be 200)"
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/health)
   echo "Response code: $status_code"
   if [ $status_code != 200 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid ID but no token (should be 401)
   echo "/subject-access-request with valid ID but no token (should be 401)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?$expected_id_type=$valid_id)
   echo "Response code: $status_code"
   if [ $status_code != 401 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi


   # Response with token without ROLE_SAR_DATA_ACCESS (should be 403)
   echo "/subject-access-request with token without ROLE_SAR_DATA_ACCESS (should be 403)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?$expected_id_type=$valid_id --header "Authorization: Bearer $no_role_token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 403 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and no ID (should be 209)
   echo "/subject-access-request with token and no ID (should be 209)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?$unexpected_id_type=$dummy_id --header "Authorization: Bearer $role_token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 209 ]; then
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and dummy ID (should be 204)
   echo "/subject-access-request with token and dummy ID (should be 204)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?$expected_id_type=$dummy_id --header "Authorization: Bearer $role_token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 204 ]; then
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and ID (should be 200)
   cmd=$endpoint/subject-access-request?$expected_id_type=$valid_id
   response=$(curl --no-progress-meter $cmd --header "Authorization: Bearer $role_token_without_quotes")
   echo "/subject-access-request with token and ID (should be 200)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $cmd --header "Authorization: Bearer $role_token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 200 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi
   echo $response

   content=$(echo $response | grep "\"content\":*")
   if [[ $content == "" ]]; then
     echo -e "** NO CONTENT BLOCK **\n"
   fi

   echo "ERRORS: $error_count"
   echo "------------------------------------------------------------------------------------------"
done


