describe('Create SGInstanceProfile', () => {
    Cypress.on('uncaught:exception', (err, runnable) => {
      return false
    });

    const namespace = Cypress.env('k8s_namespace')
    let resourceName;

    before( () => {
        cy.login()

        resourceName = Cypress._.random(0, 1e6)
    });

    beforeEach( () => {
        Cypress.Cookies.preserveOnce('sgToken')
        cy.visit(namespace + '/sginstanceprofiles/new')
    });

    it('Create SGInstanceProfile form should be visible', () => {
        cy.get('form#createProfile')
            .should('be.visible')
    });  

    after( () => {
        cy.deleteCRD('sginstanceprofiles', {
            metadata: {
                name: 'profile-' + resourceName,
                namespace: namespace
            }
        });
    });

    it('Creating a SGInstanceProfile should be possible', () => {
        // Test Config Name
        cy.get('[data-field="metadata.name"]')
            .type('profile-' + resourceName)
        
        // Test Memory
        cy.get('input[data-field="spec.memory"]')
            .type('2')
        
        // Test Submit CPU
        cy.get('input[data-field="spec.cpu"]')
            .type('1')

        // Test Huge Pages
        cy.get('input[data-field="spec.hugePages.hugepages-2Mi"]')
            .type('1')
        cy.get('input[data-field="spec.hugePages.hugepages-1Gi"]')
            .type('1')

        // Test Submit form
        cy.get('form#createProfile button[type="submit"]')
            .click()
        
        cy.get('#notifications .message.show .title')
            .should(($notification) => {
                expect($notification).contain('Profile "profile-' + resourceName + '" created successfully')
            })

        cy.location('pathname').should('eq', '/admin/' + namespace + '/sginstanceprofiles')
    });
})