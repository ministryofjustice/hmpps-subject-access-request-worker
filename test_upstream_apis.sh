#!/bin/bash

#### SETUP ####

# The directory this file is in must contain a file named 'sar_endpoint_config' which includes the following fields:
# client_with_sar_role=
# secret_for_client_with_sar_role=""

# client_without_sar_role=
# secret_for_client_without_sar_role=""

# valid_nomis_id=
# valid_ndelius_id=

# dummy_nomis_id=
# dummy_ndelius_id=

###############

. sar_endpoint_config

# Get auth token with ROLE_SAR_DATA_ACCESS
auth_response=$(curl --no-progress-meter -s -X POST "https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token?grant_type=client_credentials" \ -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n $client_with_sar_role:$secret_for_client_with_sar_role | base64)")
token=$( echo $auth_response | grep "\"access_token\":*" | awk -F\: '{print $2}' | awk -F\, '{print $1}')
token_without_quotes=$(eval echo $token)

# Get auth token without ROLE_SAR_DATA_ACCESS
auth_response_2=$(curl -s --no-progress-meter -X POST "https://sign-in-dev.hmpps.service.justice.gov.uk/auth/oauth/token?grant_type=client_credentials" \ -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n $client_without_sar_role:$secret_for_client_without_sar_role | base64)")
no_role_token=$(echo $auth_response_2 | grep "\"access_token\":*" | awk -F\: '{print $2}' | awk -F\, '{print $1}')
no_role_token_without_quotes=$(eval echo $no_role_token)

# Declare endpoints
declare -a endpoints=(
# https://complexity-of-need-staging.hmpps.service.justice.gov.uk 
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
https://learningandworkprogress-api-dev.hmpps.service.justice.gov.uk
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
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request)
   echo "Response code: $status_code"
   if [ $status_code != 401 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi


   # Response with token without ROLE_SAR_DATA_ACCESS (should be 403)
   echo "/subject-access-request with token without ROLE_SAR_DATA_ACCESS (should be 403)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?prn=$valid_nomis_id --header "Authorization: Bearer $no_role_token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 403 ]; then 
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and no ID (should be 209)
   echo "/subject-access-request with token and no ID (should be 209)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?crn=$valid_ndelius_id --header "Authorization: Bearer $token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 209 ]; then
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and dummy ID (should be 204)
   echo "/subject-access-request with token and dummy ID (should be 204)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $endpoint/subject-access-request?prn=$dummy_nomis_id --header "Authorization: Bearer $token_without_quotes")
   echo "Response code: $status_code"
   if [ $status_code != 204 ]; then
     echo -e "\n** FLAG **\n" 
     error_count=$((error_count+1))
   fi

   # Response with valid token and ID (should be 200)
   cmd=$endpoint/subject-access-request?prn=$valid_nomis_id
   response=$(curl --no-progress-meter $cmd --header "Authorization: Bearer $token_without_quotes")
   echo "/subject-access-request with token and ID (should be 200)" 
   status_code=$(curl --no-progress-meter --write-out %{http_code} --silent --output /dev/null $cmd --header "Authorization: Bearer $token_without_quotes")
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


