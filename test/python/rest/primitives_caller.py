from streamsx.rest_primitives import *

def check_instance(tc, instance):
    """Basic test of calls against an instance, assumed there is
    at least one job running.
    """
    _fetch_from_instance(tc, instance)
    instance.refresh()
    _fetch_from_instance(tc, instance)

def _fetch_from_instance(tc, instance):
    _check_non_empty_list(tc, instance.get_hosts(), Host)
    _check_non_empty_list(tc, instance.get_pes(), PE)
    _check_non_empty_list(tc, instance.get_jobs(), Job)
    _check_non_empty_list(tc, instance.get_operators(), Operator)
    _check_non_empty_list(tc, instance.get_views(), View)
    _check_non_empty_list(tc, instance.get_operator_connections(), OperatorConnection)
    _check_non_empty_list(tc, instance.get_resource_allocations(), ResourceAllocation)
    _check_non_empty_list(tc, instance.get_active_services(), ActiveService)

    _check_list(tc, instance.get_exported_streams(), ExportedStream)
    _check_list(tc, instance.get_imported_streams(), ImportedStream)
    _check_list(tc, instance.get_pe_connections(), PEConnection)

    tc.assertIsInstance(instance.get_domain(), Domain)

def check_job(tc, job):
    """Basic test of calls against an Job """
    _fetch_from_job(tc, job)
    job.refresh()
    _fetch_from_job(tc, job)

def _fetch_from_job(tc, job):
    _check_non_empty_list(tc, job.get_hosts(), Host)
    _check_non_empty_list(tc, job.get_pes(), PE)
    _check_non_empty_list(tc, job.get_operators(), Operator)
    _check_non_empty_list(tc, job.get_views(), View)
    _check_non_empty_list(tc, job.get_operator_connections(), OperatorConnection)
    _check_non_empty_list(tc, job.get_resource_allocations(), ResourceAllocation)

    _check_list(tc, job.get_pe_connections(), PEConnection)

    tc.assertIsInstance(job.get_instance(), Instance)
    tc.assertIsInstance(job.get_domain(), Domain)

def _check_non_empty_list(tc, items, expect_class):
    tc.assertTrue(items)
    _check_list(tc, items, expect_class)

def _check_list(tc, items, expect_class):
    for item in items:
        tc.assertIsInstance(item, expect_class)
